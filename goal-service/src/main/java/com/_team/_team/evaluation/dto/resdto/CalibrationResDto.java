package com._team._team.evaluation.dto.resdto;

import com._team._team.evaluation.domain.EvaluationCalibration;
import com._team._team.evaluation.domain.enums.CalibrationRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationResDto {

    private UUID calibrationId;
    private UUID responseId;
    private UUID evaluatorId;
    private CalibrationRole role;
    private String suggestedGradeJson;
    private String finalGradeJson;
    private String comment;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CalibrationResDto from(EvaluationCalibration c) {
        return CalibrationResDto.builder()
                .calibrationId(c.getCalibrationId())
                .responseId(c.getResponseId())
                .evaluatorId(c.getEvaluatorId())
                .role(c.getRole())
                .suggestedGradeJson(c.getSuggestedGradeJson())
                .finalGradeJson(c.getFinalGradeJson())
                .comment(c.getComment())
                .submittedAt(c.getSubmittedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
