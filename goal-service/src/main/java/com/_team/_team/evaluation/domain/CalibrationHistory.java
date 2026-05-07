package com._team._team.evaluation.domain;

import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 캘리브레이션 조정 감사 이력.
 * 한 응답에 대한 매 조정(등급/사유)을 누적 저장하여 감사 추적성을 확보합니다.
 */
@Entity
@Table(name = "calibration_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CalibrationHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "calibration_history_id")
    private UUID calibrationHistoryId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "season_id", nullable = false)
    private UUID seasonId;

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "adjusted_grade", length = 32)
    private String adjustedGrade;

    @Column(name = "adjustment_reason", columnDefinition = "TEXT")
    private String adjustmentReason;

    @Column(name = "previous_grade", length = 32)
    private String previousGrade;

    @Column(name = "adjusted_by", nullable = false)
    private UUID adjustedBy;
}
