package com._team._team.evaluation.scheduler;

import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import com._team._team.evaluation.repository.EvaluationSeasonRepository;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * SelfEvalDeadlineScheduler
 *
 *  자기평가 마감일이 지난 시즌의 SELF_PENDING 응답을 자동 SUBMITTED(empty) 처리.
 *  매일 새벽 3시 실행.
 *
 *  마감 기준: scheduleJson 의 SELF phase end (없으면 season.startDate + 7일)
 *  Phase 1 단순화: 시즌 startDate 가 지난 ACTIVE 시즌의 SELF_PENDING 응답을 자동 처리
 */
@Component
public class SelfEvalDeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(SelfEvalDeadlineScheduler.class);

    private final EvaluationSeasonRepository seasonRepository;
    private final EvaluationResponseRepository responseRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SelfEvalDeadlineScheduler(EvaluationSeasonRepository seasonRepository,
                                     EvaluationResponseRepository responseRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.seasonRepository = seasonRepository;
        this.responseRepository = responseRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 매일 03:00 실행 */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void runDaily() {
        LocalDate today = LocalDate.now();
        // ACTIVE 시즌 중 자기평가 마감 지난 것
        // (정확한 deadline 계산은 scheduleJson 파싱 필요 — 단순화 버전)
        List<EvaluationSeason> seasons = seasonRepository.findAll().stream()
                .filter(s -> s.getStatus().name().equals("ACTIVE"))
                .filter(s -> s.getStartDate() != null && today.isAfter(s.getStartDate().plusDays(7)))
                .toList();

        for (EvaluationSeason s : seasons) {
            int processed = processSeason(s);
            if (processed > 0) {
                log.info("[SelfEvalDeadline] season={} auto-submitted={}",
                        s.getSeasonId(), processed);
            }
        }
    }

    private int processSeason(EvaluationSeason season) {
        // 시즌의 모든 SELF_PENDING 응답을 EMPTY 로 자동 제출
        List<EvaluationResponse> pendings = responseRepository
                .findByCompanyIdAndStageOrderByCreatedAtDesc(
                        season.getCompanyId(), EvaluationStage.SELF_PENDING);

        // 시즌에 속한 것만 필터 (group.season.seasonId 비교)
        List<EvaluationResponse> targets = pendings.stream()
                .filter(r -> r.getGroup() != null
                          && r.getGroup().getSeason() != null
                          && r.getGroup().getSeason().getSeasonId().equals(season.getSeasonId()))
                .toList();

        for (EvaluationResponse r : targets) {
            r.autoSubmitEmpty();
            eventPublisher.publishEvent(NotificationMessage.builder()
                    .receiverId(r.getTargetMemberId())
                    .senderId(null)
                    .notificationType(NotificationType.EVALUATION_REMINDER)
                    .content("자기평가가 미제출 처리되었습니다. 마감일 도래로 빈 값 자동 제출되었습니다.")
                    .targetId(r.getResponseId())
                    .targetType("EVALUATION_RESPONSE")
                    .build());
        }
        return targets.size();
    }
}
