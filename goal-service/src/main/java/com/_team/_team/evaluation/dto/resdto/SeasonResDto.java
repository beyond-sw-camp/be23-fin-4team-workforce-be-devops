package com._team._team.evaluation.dto.resdto;

import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.domain.enums.SeasonType;
import com._team._team.goal.domain.enums.KpiCycle;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeasonResDto {
    private UUID seasonId;
    private UUID companyId;
    private String name;
    private SeasonType type;
    private KpiCycle targetCycle;
    private LocalDate targetCycleStart;
    private LocalDate startDate;
    private LocalDate endDate;
    private SeasonStatus status;
    private LocalDate resultPublishDate;
    /** 결과 공개 시각 — null 이면 비공개 상태 */
    private LocalDateTime resultsPublishedAt;
    private String scheduleJson;

    public static SeasonResDto from(EvaluationSeason e) {
        return SeasonResDto.builder()
                .seasonId(e.getSeasonId())
                .companyId(e.getCompanyId())
                .name(e.getName())
                .type(e.getType())
                .targetCycle(e.getTargetCycle())
                .targetCycleStart(e.getTargetCycleStart())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .status(e.getStatus())
                .resultPublishDate(e.getResultPublishDate())
                .resultsPublishedAt(e.getResultsPublishedAt())
                .scheduleJson(e.getScheduleJson())
                .build();
    }
}
