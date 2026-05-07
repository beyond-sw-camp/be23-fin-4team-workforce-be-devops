package com._team._team.goal.scheduler;

import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.repository.EvaluationGroupRepository;
import com._team._team.evaluation.repository.EvaluationSeasonRepository;
import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.GoalApprovalBundle;
import com._team._team.goal.domain.enums.BundleApprovalStatus;
import com._team._team.goal.domain.enums.GoalStatus;
import com._team._team.goal.repository.GoalApprovalBundleRepository;
import com._team._team.goal.repository.GoalRepository;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * WeightShortageReportScheduler
 *
 *  시즌 활성화 임박 (D-7 ~ D-day) DRAFT 시즌의 대상자 중 가중치 결손/PENDING bundle 잔존 멤버를
 *  찾아 매일 알림.
 *  매일 08:00 실행.
 *
 *  Side effect:
 *    - 대상 멤버에게 SELF_EVAL_REMINDER + WEIGHT_SHORTAGE 알림
 *    - 시즌 admin 에게도 결손자 카운트 알림 (별도 알림)
 */
@Component
public class WeightShortageReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeightShortageReportScheduler.class);

    private final EvaluationSeasonRepository seasonRepository;
    private final EvaluationGroupRepository groupRepository;
    private final GoalRepository goalRepository;
    private final GoalApprovalBundleRepository bundleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public WeightShortageReportScheduler(EvaluationSeasonRepository seasonRepository,
                                         EvaluationGroupRepository groupRepository,
                                         GoalRepository goalRepository,
                                         GoalApprovalBundleRepository bundleRepository,
                                         ApplicationEventPublisher eventPublisher,
                                         ObjectMapper objectMapper) {
        this.seasonRepository = seasonRepository;
        this.groupRepository = groupRepository;
        this.goalRepository = goalRepository;
        this.bundleRepository = bundleRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional(readOnly = true)
    public void runDaily() {
        LocalDate today = LocalDate.now();
        // 활성화 7일 전 ~ 당일
        List<EvaluationSeason> impending = seasonRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeasonStatus.DRAFT)
                .filter(s -> s.getStartDate() != null)
                .filter(s -> {
                    LocalDate d = s.getStartDate();
                    return !today.isBefore(d.minusDays(7)) && !today.isAfter(d);
                })
                .toList();

        for (EvaluationSeason s : impending) {
            int found = scanSeason(s, today);
            if (found > 0) {
                log.info("[WeightShortage] season={} D-{} blocked={}",
                        s.getSeasonId(),
                        java.time.temporal.ChronoUnit.DAYS.between(today, s.getStartDate()),
                        found);
            }
        }
    }

    private int scanSeason(EvaluationSeason season, LocalDate today) {
        if (season.getTargetCycle() == null || season.getTargetCycleStart() == null) return 0;

        // 시즌의 모든 EvaluationGroup 의 targetMemberIds 평탄화
        List<UUID> targets = new ArrayList<>();
        groupRepository.findBySeason_SeasonId(season.getSeasonId()).forEach(g -> {
            try {
                String json = g.getTargetMemberIdsJson();
                if (json != null) {
                    targets.addAll(objectMapper.readValue(json, new TypeReference<List<UUID>>() {}));
                }
            } catch (Exception ignored) {}
        });

        int reported = 0;
        for (UUID memberId : targets) {
            List<Goal> active = goalRepository.findByOwnerIdAndCycleAndCycleStartDateAndStatusIn(
                    memberId, season.getTargetCycle(), season.getTargetCycleStart(),
                    EnumSet.of(GoalStatus.ACTIVE));

            int sum = active.stream().mapToInt(Goal::getWeightPct).sum();
            boolean weightShort = sum != 100;

            List<GoalApprovalBundle> pending = bundleRepository
                    .findByRequestedByAndCompanyIdAndStatus(
                            memberId, season.getCompanyId(), BundleApprovalStatus.PENDING);
            boolean hasPending = !pending.isEmpty();

            if (weightShort || hasPending) {
                String body = String.format(
                        "시즌 활성화까지 D-%d. 가중치 합 %d/100, 미결정 승인 %d건. 즉시 정리 필요.",
                        java.time.temporal.ChronoUnit.DAYS.between(today, season.getStartDate()),
                        sum, pending.size());
                eventPublisher.publishEvent(NotificationMessage.builder()
                        .receiverId(memberId)
                        .senderId(null)
                        .notificationType(NotificationType.EVALUATION_REMINDER)
                        .content(body)
                        .targetId(season.getSeasonId())
                        .targetType("EVALUATION_SEASON")
                        .build());
                reported++;
            }
        }
        return reported;
    }
}
