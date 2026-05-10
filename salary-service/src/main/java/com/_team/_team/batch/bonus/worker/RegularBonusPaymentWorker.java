package com._team._team.batch.bonus.worker;

import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.repository.MemberLeaveOfAbsenceRepository;
import com._team._team.dto.ApiResponse;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import com._team._team.salary.domain.BonusPolicy;
import com._team._team.salary.domain.enums.BonusEligibilityScope;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.repository.BonusPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 정기 상여 발행 안내
 * 매월 1일 실행 - 그 달이 지급월이면 인사관리자/대상자에게 발행 안내 알림
 * 실제 발행은 관리자가 [상여 발행] 화면에서 수동 처리
 */
@Slf4j
@Component
public class RegularBonusPaymentWorker {

    // 매월 1일 실행 가정 - cron 으로 보장
    private static final int STANDARD_TRIGGER_DAY_OF_MONTH = 1;

    private final BonusPolicyRepository bonusPolicyRepository;
    private final MemberFeignClient memberFeignClient;
    private final MemberLeaveOfAbsenceRepository leaveOfAbsenceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RegularBonusPaymentWorker(BonusPolicyRepository bonusPolicyRepository,
                                     MemberFeignClient memberFeignClient,
                                     MemberLeaveOfAbsenceRepository leaveOfAbsenceRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.bonusPolicyRepository = bonusPolicyRepository;
        this.memberFeignClient = memberFeignClient;
        this.leaveOfAbsenceRepository = leaveOfAbsenceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result run(LocalDate today) {
        Result result = new Result();

        // 매월 1일
        if (today.getDayOfMonth() != STANDARD_TRIGGER_DAY_OF_MONTH) {
            log.info("[RegularBonus] today={} 매월 1일 아님, 스킵", today);
            return result;
        }

        List<BonusPolicy> policies = bonusPolicyRepository.findAllActiveRegularBonusPoliciesAt(today);
        log.info("[RegularBonus] 활성 정책 {} 개", policies.size());

        for (BonusPolicy policy : policies) {
            if (!isPaymentMonth(today, policy.getRegularBonusPaymentCount())) {
                result.skipPolicy++;
                continue;
            }
            try {
                processPolicy(policy, today, result);
            } catch (Exception e) {
                log.error("[RegularBonus] policy={} 처리 실패", policy.getBonusPolicyId(), e);
                result.errorPolicy++;
            }
        }

        log.info("[RegularBonus] 완료 date={} eligibleMembers={} skippedMembers={} skipPolicy={} errorPolicy={}",
                today, result.eligibleMembers, result.skippedMembers, result.skipPolicy, result.errorPolicy);
        return result;
    }

    // 회사별 호출, 회사 1곳의 활성 정기상여 정책만 처리
    @Transactional
    public Result runForCompany(UUID companyId, LocalDate today) {
        Result result = new Result();

        if (today.getDayOfMonth() != STANDARD_TRIGGER_DAY_OF_MONTH) {
            log.info("[RegularBonus] companyId={} today={} 매월 1일 아님, 스킵", companyId, today);
            return result;
        }

        List<BonusPolicy> all = bonusPolicyRepository.findAllActiveRegularBonusPoliciesAt(today);
        List<BonusPolicy> policies = all.stream().filter(p -> companyId.equals(p.getCompanyId())).toList();

        for (BonusPolicy policy : policies) {
            if (!isPaymentMonth(today, policy.getRegularBonusPaymentCount())) {
                result.skipPolicy++;
                continue;
            }
            try {
                processPolicy(policy, today, result);
            } catch (Exception e) {
                log.error("[RegularBonus] companyId={} policy={} 처리 실패", companyId, policy.getBonusPolicyId(), e);
                result.errorPolicy++;
            }
        }

        log.info("[RegularBonus] 회사별 완료 companyId={} date={} eligibleMembers={} skippedMembers={}",
                companyId, today, result.eligibleMembers, result.skippedMembers);
        return result;
    }

    /** 정기 상여 지급 월인지 판정 - 연 지급 횟수 기준 */
    private boolean isPaymentMonth(LocalDate today, Integer count) {
        if (count == null || count <= 0) return false;
        int month = today.getMonthValue();
        return switch (count) {
            case 12 -> true;                                    // 매월
            case 6 -> month % 2 == 0;                           // 짝수달 (2,4,6,8,10,12)
            case 4 -> month == 3 || month == 6 || month == 9 || month == 12; // 분기말
            case 2 -> month == 6 || month == 12;                // 반기말
            case 1 -> month == 12;                              // 연말
            default -> false;
        };
    }

    /** 회사별 자격 검증 + 알림 발행 */
    private void processPolicy(BonusPolicy policy, LocalDate today, Result result) {
        UUID companyId = policy.getCompanyId();

        // 1. 회사 직원 목록 조회 (Feign)
        List<MemberResDto> members = fetchMembers(companyId);
        if (members.isEmpty()) {
            log.warn("[RegularBonus] companyId={} 직원 0명 - 스킵", companyId);
            return;
        }

        // 2. 휴직자 맵 (excludeOnLeaveYn='Y' 일 때만 조회)
        Map<UUID, MemberLeaveOfAbsence> loaMap = "Y".equals(policy.getExcludeOnLeaveYn())
                ? loadActiveLeaveOfAbsenceMap(companyId, today)
                : Map.of();

        // 3. 자격 검증 + 알림
        for (MemberResDto member : members) {
            if (!isEligible(member, policy, loaMap, today)) {
                result.skippedMembers++;
                continue;
            }
            publishBonusNotification(policy, member);
            result.eligibleMembers++;
        }
    }

    /** 자격 검증 - eligibilityScope, minTenureMonths, excludeOnLeaveYn */
    private boolean isEligible(MemberResDto member, BonusPolicy policy,
                                Map<UUID, MemberLeaveOfAbsence> loaMap, LocalDate today) {
        // 입사일 누락 직원은 안전하게 제외
        LocalDate joinDate = member.getJoinDate();
        if (joinDate == null) return false;

        // 1. 최소 근속 월수 검증
        int minTenure = policy.getMinTenureMonths() != null ? policy.getMinTenureMonths() : 0;
        if (minTenure > 0) {
            long monthsSince = ChronoUnit.MONTHS.between(joinDate, today);
            if (monthsSince < minTenure) return false;
        }

        // 2. 휴직자 제외 - excludeOnLeaveYn='Y' 정책에서만 차단
        if ("Y".equals(policy.getExcludeOnLeaveYn()) && loaMap.containsKey(member.getMemberId())) {
            return false;
        }

        // 3. 지급 대상 범위 - REGULAR_ONLY 면 정규직만 (MemberResDto.employmentType 등 활용 가능 시)
        // 현재 MemberResDto 에 고용형태 필드가 명시적으로 없으므로 ALL 만 적용. 추후 정규직 식별 필드 추가 시 확장.
        if (policy.getEligibilityScope() == BonusEligibilityScope.REGULAR_ONLY) {
            // TODO: member.getEmploymentType() 필드 추가 시 정규직 검증
        }

        return true;
    }

    private List<MemberResDto> fetchMembers(UUID companyId) {
        try {
            ApiResponse<List<MemberResDto>> response = memberFeignClient.getMembersByCompany(companyId);
            return response != null && response.getData() != null ? response.getData() : List.of();
        } catch (Exception e) {
            log.error("[RegularBonus] companyId={} 직원 조회 실패", companyId, e);
            return List.of();
        }
    }

    /** 활성 휴직자 Map (memberId -> MemberLeaveOfAbsence) */
    private Map<UUID, MemberLeaveOfAbsence> loadActiveLeaveOfAbsenceMap(UUID companyId, LocalDate today) {
        return leaveOfAbsenceRepository
                .findAllActiveInCompanyOnDate(companyId, today)
                .stream()
                .collect(Collectors.toMap(
                        MemberLeaveOfAbsence::getMemberId,
                        Function.identity(),
                        (a, b) -> a));
    }

    // 직원에게 이번 달 정기 상여 안내 - 실제 발행/지급은 관리자가 [상여 발행]에서 처리
    private void publishBonusNotification(BonusPolicy policy, MemberResDto member) {
        String content = String.format(
                "[정기 상여] 이번 달 정기 상여 대상자입니다. 연 누계 비율 %s%%, 연 %d 회 정책 적용.",
                policy.getRegularBonusAnnualRate() != null ? policy.getRegularBonusAnnualRate().toPlainString() : "-",
                policy.getRegularBonusPaymentCount() != null ? policy.getRegularBonusPaymentCount() : 0);
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(member.getMemberId())
                .senderId(null)
                .notificationType(NotificationType.BONUS_PAYMENT)
                .content(content)
                .targetId(policy.getBonusPolicyId())
                .targetType("BONUS_POLICY")
                .build());
    }

    public static class Result {
        public int eligibleMembers = 0;
        public int skippedMembers = 0;
        public int skipPolicy = 0;
        public int errorPolicy = 0;
    }
}
