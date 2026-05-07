package com._team._team.evaluation.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.evaluation.domain.enums.CalibrationRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EvaluationCalibration (신규)
 *
 *  다중 평가자 등급 조정 입력. (response_id, evaluator_id) unique.
 *
 *   role = LEAD       → finalGradeJson 입력 권한, confirm 책임자
 *   role = ASSISTANT  → suggestedGradeJson 만 입력
 *
 *  JSON 형식:
 *    suggestedGradeJson : { "<criteriaId>": "A", "<criteriaId2>": "B", ... }
 *    finalGradeJson     : 동일 형식, LEAD 만 작성
 */
@Entity
@Table(
    name = "evaluation_calibration",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_calibration_resp_eval",
        columnNames = {"response_id", "evaluator_id"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationCalibration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "calibration_id")
    private UUID calibrationId;

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "evaluator_id", nullable = false)
    private UUID evaluatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private CalibrationRole role;

    @Column(name = "suggested_grade_json", columnDefinition = "TEXT")
    private String suggestedGradeJson;

    @Column(name = "final_grade_json", columnDefinition = "TEXT")
    private String finalGradeJson;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public void saveSuggested(String suggestedGradeJson, String comment) {
        this.suggestedGradeJson = suggestedGradeJson;
        this.comment = comment;
    }

    public void saveFinal(String finalGradeJson, String comment) {
        if (this.role != CalibrationRole.LEAD) {
            throw new IllegalStateException("finalGrade 는 LEAD 만 입력 가능");
        }
        this.finalGradeJson = finalGradeJson;
        this.comment = comment;
    }

    public void submit() {
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
    }

    public boolean isSubmitted() {
        return this.submittedAt != null;
    }
}
