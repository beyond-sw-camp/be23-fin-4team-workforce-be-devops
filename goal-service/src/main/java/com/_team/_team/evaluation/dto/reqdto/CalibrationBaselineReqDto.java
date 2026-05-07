package com._team._team.evaluation.dto.reqdto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CalibrationBaselineReqDto {
    private String range; // team, individual
    private Double baselineValue;
}
