package com._team._team.evaluation.scheduler;

import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import com._team._team.evaluation.repository.EvaluationSeasonRepository;
import com._team._team.evaluation.util.EvaluationSchedule;
import com._team._team.evaluation.util.EvaluationSchedule.Phase;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * StageTransitionScheduler
 *
 *  scheduleJson 의 phase 일정에 따라 EvaluationResponse.stage 자동 전이.
 *  매일 04:00 실행 (자기평가 마감 자동전이 후).
 *
 *  전이 규칙 (오늘 날짜가 phase.start 이상):
 *    SELF_SUBMITTED → (다음 phase 가 PEER/UPWARD/DOWNWARD 면 그쪽으로) → CALIBRATION_OPEN
 *
 *  CALIBRATION_OPEN/LOCKED → CONFIRMED 는 Lead 가 confirm 으로 결정 (자동 전이 X)
 */
@Component
public class StageTransitionScheduler {

    private static final Logger log = LoggerFactory.getLogger(StageTransitionScheduler.class);

    private final EvaluationSeasonRepository seasonRepository;
    private final EvaluationResponseRepository responseRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public StageTransitionScheduler(EvaluationSeasonRepository seasonRepository,
                                    EvaluationResponseRepository responseRepository,
                                    ApplicationEventPublisher eventPublisher,
                                    ObjectMapper objectMapper) {
        this.seasonRepository = seasonRepository;
        this.responseRepository = responseRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void runDaily() {
        LocalDate today = LocalDate.now();
        List<EvaluationSeason> active = seasonRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeasonStatus.SELF_EVAL
                        || s.getStatus() == SeasonStatus.MANAGER_EVAL)
                .toList();

        for (EvaluationSeason s : active) {
            int moved = transitionSeason(s, today);
            if (moved > 0) {
                log.info("[StageTransition] season={} responses moved={}", s.getSeasonId(), moved);
            }
        }
    }

    private int transitionSeason(EvaluationSeason season, LocalDate today) {
        EvaluationSchedule schedule = EvaluationSchedule.parse(season.getScheduleJson(), objectMapper);

        // 시즌의 모든 응답 조회 (group → season 매칭)
        // 단순화: stage 별로 조회 후 group.season 필터
        int moved = 0;
        moved += tryAdvance(season, today, schedule, EvaluationStage.SELF_SUBMITTED);
        moved += tryAdvance(season, today, schedule, EvaluationStage.PEER_OPEN);
        moved += tryAdvance(season, today, schedule, EvaluationStage.UPWARD_OPEN);
        moved += tryAdvance(season, today, schedule, EvaluationStage.DOWNWARD_OPEN);
        return moved;
    }

    private int tryAdvance(EvaluationSeason season, LocalDate today,
                           EvaluationSchedule schedule, EvaluationStage from) {
        EvaluationStage to = schedule.nextStage(from);
        if (to == from) return 0;

        // to phase 의 start 일자가 도래했는지
        Phase next = schedule.findPhase(to);
        if (next == null || next.getStart() == null) return 0;
        if (today.isBefore(next.getStart())) return 0;

        List<EvaluationResponse> candidates = responseRepository
                .findByCompanyIdAndStageOrderByCreatedAtDesc(season.getCompanyId(), from);
        List<EvaluationResponse> targets = candidates.stream()
                .filter(r -> r.getGroup() != null
                          && r.getGroup().getSeason() != null
                          && r.getGroup().getSeason().getSeasonId().equals(season.getSeasonId()))
                .toList();

        for (EvaluationResponse r : targets) {
            advance(r, to);
        }
        return targets.size();
    }

    private void advance(EvaluationResponse r, EvaluationStage to) {
        switch (to) {
            case PEER_OPEN, UPWARD_OPEN, DOWNWARD_OPEN -> r.moveToOptionalStage(to);
            case CALIBRATION_OPEN -> r.openCalibration();
            default -> {}
        }
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(r.getEvaluatorId())
                .senderId(null)
                .notificationType(NotificationType.EVALUATION_REOPENED)
                .content("평가 단계가 진행되었습니다. 단계: " + to.name())
                .targetId(r.getResponseId())
                .targetType("EVALUATION_RESPONSE")
                .build());
    }
}
