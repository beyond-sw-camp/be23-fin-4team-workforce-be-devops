package com._team._team.evaluation.scheduler;

import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import com._team._team.evaluation.repository.EvaluationSeasonRepository;
import com._team._team.evaluation.service.EvaluationSeasonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 평가 결과 자동 공개 스케줄러.
 *
 * 매 분마다 다음 조건을 모두 만족하는 시즌을 찾아 결과를 공개합니다:
 *  - status = ACTIVE
 *  - resultsPublishedAt == null (아직 미공개)
 *  - resultPublishDate != null && resultPublishDate <= today()
 *  - 해당 시즌의 **모든 응답이 종료 상태(CONFIRMED 또는 SKIPPED_LEAVER)**
 *
 * 자기평가만 제출된 단계(SELF_SUBMITTED), calibration 진행 중(CALIBRATION_OPEN/LOCKED),
 * 옵션 단계(PEER_OPEN / UPWARD_OPEN / DOWNWARD_OPEN) 등이 남아 있으면 공개를 보류하고
 * 다음 주기에 다시 시도합니다.
 *
 * 공개 자체는 EvaluationSeasonService.publishResults() 가 수행하며,
 * 동일 트랜잭션에서 피드백 면담도 자동 생성됩니다.
 */
@Component
public class EvaluationAutoPublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvaluationAutoPublishScheduler.class);

    private final EvaluationSeasonRepository seasonRepository;
    private final EvaluationResponseRepository responseRepository;
    private final EvaluationSeasonService seasonService;

    public EvaluationAutoPublishScheduler(EvaluationSeasonRepository seasonRepository,
                                           EvaluationResponseRepository responseRepository,
                                           EvaluationSeasonService seasonService) {
        this.seasonRepository = seasonRepository;
        this.responseRepository = responseRepository;
        this.seasonService = seasonService;
    }

    /**
     * 60초마다 실행. 다수 인스턴스 환경에서는 shedlock 등 분산 락 도입 검토.
     * 현재는 단일 인스턴스 가정 + 개별 시즌별 트랜잭션 격리로 동시성 방어.
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void autoPublishDueSeasons() {
        LocalDate today = LocalDate.now();
        List<EvaluationSeason> candidates = seasonRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeasonStatus.GRADE_CONFIRM)
                .filter(s -> s.getResultsPublishedAt() == null)
                .filter(s -> s.getResultPublishDate() != null)
                .filter(s -> !s.getResultPublishDate().isAfter(today))
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        for (EvaluationSeason season : candidates) {
            try {
                int unfinished = countUnfinishedResponses(season);
                if (unfinished > 0) {
                    log.info("[auto-publish] skip season={} ({}) — 미확정 응답 {}건",
                            season.getSeasonId(), season.getName(), unfinished);
                    continue;
                }
                // publishResults 가 동일 트랜잭션에서 면담 생성까지 수행
                seasonService.publishResults(season.getSeasonId(), season.getCompanyId());
                log.info("[auto-publish] published season={} ({})",
                        season.getSeasonId(), season.getName());
            } catch (Exception e) {
                log.warn("[auto-publish] failed season={} ({}): {}",
                        season.getSeasonId(), season.getName(), e.getMessage());
            }
        }
    }

    /**
     * 종료되지 않은 응답 수를 반환.
     *  - 종료 상태: CONFIRMED, SKIPPED_LEAVER
     *  - 그 외 모든 단계(SELF_PENDING / SELF_SUBMITTED / *_OPEN / CALIBRATION_*)는 미확정으로 간주
     */
    private int countUnfinishedResponses(EvaluationSeason season) {
        List<EvaluationResponse> all = responseRepository.findByGroup_Season_SeasonId(season.getSeasonId());
        return (int) all.stream()
                .filter(r -> r.getCompanyId().equals(season.getCompanyId()))
                .filter(r -> r.getStage() != EvaluationStage.CONFIRMED
                          && r.getStage() != EvaluationStage.SKIPPED_LEAVER)
                .count();
    }
}
