package com._team._team.evaluation.dto.reqdto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CalibrationAdjustReqDto {
    private List<Adjustment> adjustments;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Adjustment {
        private UUID responseId;
        private String adjustedGrade;
        private String adjustmentReason;
    }
}
