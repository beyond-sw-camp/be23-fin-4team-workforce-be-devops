package com._team._team.batch.leave.worker;

import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.domain.LeavePromotionLog;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.PromotionLogStatus;
import com._team._team.attendance.domain.enums.PromotionStage;
import com._team._team.attendance.repository.LeavePolicyRepository;
import com._team._team.attendance.repository.LeavePromotionLogRepository;
import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.attendance.service.LeavePromotionResponseService;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 연차사용촉진제 알림 배치 (근로기준법 61조)
 * 매일 만료일 기준 1차/2차 촉진 대상 잔고에 알림 발송, 이력 기록
 */
@Slf4j
@Component
public class LeavePromotionWorker {

    private final LeavePolicyRepository leavePolicyRepository;
    private final MemberBalanceRepository memberBalanceRepository;
    private final LeavePromotionLogRepository promotionLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LeavePromotionResponseService leavePromotionResponseService;

    @Autowired
    public LeavePromotionWorker(LeavePolicyRepository leavePolicyRepository,
                                MemberBalanceRepository memberBalanceRepository,
                                LeavePromotionLogRepository promotionLogRepository,
                                ApplicationEventPublisher eventPublisher,
                                LeavePromotionResponseService leavePromotionResponseService) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.memberBalanceRepository = memberBalanceRepository;
        this.promotionLogRepository = promotionLogRepository;
        this.eventPublisher = eventPublisher;
        this.leavePromotionResponseService = leavePromotionResponseService;
    }

    public Result run(LocalDate today) {
        Result result = new Result();

        List<LeavePolicy> policies = leavePolicyRepository
                .findByIsPromotionYnAndDelYn("Y", "N");

        for (LeavePolicy policy : policies) {
            processStage(policy, today, PromotionStage.FIRST,
                    policy.getPromotion1stBeforeDays(), result);
            processStage(policy, today, PromotionStage.SECOND,
                    policy.getPromotion2ndBeforeDays(), result);
        }

        log.info("[LeavePromotion] date={} 1차={} 2차={} skip={}",
                today, result.firstSent, result.secondSent, result.skipped);
        return result;
    }

    // 단계별 촉진 대상 조회 후 알림 + 로그 저장
    // 외부 @Transactional 없으므로 각 saveLog 는 JpaRepository 기본 트랜잭션으로 즉시 commit, (포폴에 쓸 수도..)
    // 그 후 autoDesignateOnSecondNotice(REQUIRES_NEW)가 commit 된 row 를 정상 조회
    private void processStage(LeavePolicy policy, LocalDate today,
                              PromotionStage stage, Integer beforeDays, Result result) {
        if (beforeDays == null || beforeDays <= 0) return;

        LocalDate targetExpiration = today.plusDays(beforeDays);
        List<MemberBalance> balances = memberBalanceRepository
                .findForPromotion(policy.getCompanyId(), targetExpiration);

        for (MemberBalance b : balances) {
            // 이미 같은 단계 발송 이력 있으면 스킵
            if (promotionLogRepository.existsByMemberBalanceIdAndStage(
                    b.getMemberBalanceId(), stage)) {
                result.skipped++;
                continue;
            }

            // 추가 가드 1차에서 이미 회신 받은 잔고는 2차 발송 안 함
            // 직원이 회신했다면 회사 면책 완료 추가 통지 불필요
            if (stage == PromotionStage.SECOND
                    && hasAcknowledgedFirst(b.getMemberBalanceId())) {
                result.skipped++;
                continue;
            }

            publishPromotion(b, stage);
            LeavePromotionLog savedLog = promotionLogRepository.save(LeavePromotionLog.builder()
                    .memberBalanceId(b.getMemberBalanceId())
                    .memberId(b.getMemberId())
                    .companyId(b.getCompanyId())
                    .stage(stage)
                    .sentOn(today)
                    .status(PromotionLogStatus.SENT)
                    .build());
            if (stage == PromotionStage.FIRST) {
                result.firstSent++;
            } else {
                result.secondSent++;
                // 근로기준법 61조 - 2차 통보 시점 자동 강제 지정
                // 1차 회신 없이 2차 도달한 경우 회사가 만료일 직전 평일 N일을 자동 지정
                try {
                    leavePromotionResponseService.autoDesignateOnSecondNotice(
                            savedLog.getPromotionLogId(), null);
                } catch (Exception e) {
                    log.error("[LeavePromotion] 2차 자동 지정 실패 logId={}",
                            savedLog.getPromotionLogId(), e);
                }
            }
        }
    }

    // 같은 잔고 1차 통보가 ACKNOWLEDGED 상태인지 검사
    private boolean hasAcknowledgedFirst(UUID memberBalanceId) {
        return promotionLogRepository
                .findByMemberBalanceIdAndStatus(memberBalanceId, PromotionLogStatus.ACKNOWLEDGED)
                .stream()
                .anyMatch(l -> l.getStage() == PromotionStage.FIRST);
    }
    private void publishPromotion(MemberBalance b, PromotionStage stage) {
        NotificationType type = stage == PromotionStage.FIRST
                ? NotificationType.LEAVE_PROMOTION_FIRST
                : NotificationType.LEAVE_PROMOTION_SECOND;
        // 1차: 사용 계획 회신 요청 / 2차: 회사 자동 지정 통보 안내
        String content = stage == PromotionStage.FIRST
                ? "[연차 촉진 1차] 남은 연차 " + b.getRemaining() + "일, 만료일 "
                    + b.getExpirationDate() + ". 사용 계획을 회신해주세요."
                : "[연차 촉진 2차] 1차 통보 미회신으로 회사가 사용일을 자동 지정 통보합니다 (근로기준법 61조). 만료일 "
                    + b.getExpirationDate();
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(b.getMemberId())
                .senderId(null)
                .notificationType(type)
                .content(content)
                .targetId(b.getMemberBalanceId())
                .targetType("MEMBER_BALANCE")
                .build());
    }

    public static class Result {
        public int firstSent = 0;
        public int secondSent = 0;
        public int skipped = 0;
    }
}