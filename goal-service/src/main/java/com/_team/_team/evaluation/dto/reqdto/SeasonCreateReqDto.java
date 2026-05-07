package com._team._team.evaluation.dto.reqdto;

import com._team._team.evaluation.domain.enums.SeasonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeasonCreateReqDto {
    @NotBlank private String name;
    @NotNull private SeasonType type;
    /** 봉인·조회에 쓰는 OKR 회차 시작일 (목표 Goal.cycleStartDate 와 동일해야 함). 운영 기간 startDate 와 별개. */
    @NotNull private LocalDate targetCycleStart;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    private LocalDate resultPublishDate;
    private String scheduleJson;
}
