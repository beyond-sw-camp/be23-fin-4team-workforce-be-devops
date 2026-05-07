package com._team._team.evaluation.dto.reqdto;

import com._team._team.evaluation.domain.enums.SeasonType;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeasonUpdateReqDto {
    private String name;
    private SeasonType type;
    /** DRAFT 에서만 변경. null 이면 기존 값 유지. */
    private LocalDate targetCycleStart;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate resultPublishDate;
    private String scheduleJson;
}
