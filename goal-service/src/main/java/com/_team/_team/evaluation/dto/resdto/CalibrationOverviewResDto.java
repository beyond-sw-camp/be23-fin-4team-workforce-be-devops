package com._team._team.evaluation.dto.resdto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CalibrationOverviewResDto {
    private Map<String, Double> targetDistribution;
    private Map<String, Double> currentDistribution;
    private List<CalibrationMemberDto> members;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CalibrationMemberDto {
        private String responseId;
        private String memberId;
        private String memberName;
        private String teamName;
        private Double score;
        private String currentGrade;
        private String adjustedGrade;
        private String adjustmentReason;
        private String confirmedAt;
    }
}
