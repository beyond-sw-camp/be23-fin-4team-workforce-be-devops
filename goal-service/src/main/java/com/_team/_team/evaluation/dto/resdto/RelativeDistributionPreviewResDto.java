package com._team._team.evaluation.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelativeDistributionPreviewResDto {
    private Map<String, Double> targetDistribution;
    private Map<String, Double> currentDistribution;
    private Map<String, Double> predictedDistribution;
    private List<PreviewAdjustment> adjustments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewAdjustment {
        private UUID responseId;
        private BigDecimal normalizedScore;
        private String currentGrade;
        private String predictedGrade;
    }
}
