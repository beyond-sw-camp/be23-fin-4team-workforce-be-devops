package com._team._team.evaluation.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.evaluation.domain.enums.EvalType;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.domain.enums.EvaluationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EvaluationResponse (확장)
 *
 *  추가:
 *   - stage              : 단계머신 (SELF_PENDING → … → CONFIRMED)
 *   - selfEvalEmpty      : 자기평가 마감 후 미제출 자동 플래그
 *   - confirmedGrade     : Lead 가 확정 시 직접 선택한 최종 등급 (S/A/B/C)
 *   - finalScoreSnapshot : 확정 시점에 1회 산출되는 최종 점수
 *   - confirmedBy / confirmedAt : 확정 책임자 추적
 *
 *  goalSnapshotJson 은 시즌 ACTIVE 전이 시점에 봉인.
 *  answersJson 은 자기평가 응답 (criteriaId 별 등급).
 *  calibrationJson 은 deprecation: EvaluationCalibration 테이블로 이전.
 */
@Entity
@Table(name = "evaluation_response")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationResponse extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "response_id")
    private UUID responseId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private EvaluationGroup group;

    @Column(name = "target_member_id", nullable = false)
    private UUID targetMemberId;

    @Column(name = "evaluator_id", nullable = false)
    private UUID evaluatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false, length = 32)
    private EvalType evaluationType;

    /** 기존 status 유지 (호환). 신규는 stage 기반으로 전환 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private EvaluationStatus status = EvaluationStatus.NOT_STARTED;

    /** 신규 단계머신 */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32)
    @Builder.Default
    private EvaluationStage stage = EvaluationStage.SELF_PENDING;

    @Column(name = "self_eval_empty", nullable = false)
    @Builder.Default
    private boolean selfEvalEmpty = false;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

    @Column(name = "answers_json", columnDefinition = "JSON")
    private String answersJson;

    /** Deprecated: EvaluationCalibration 으로 이전. 기존 호환만 위해 유지 */
    @Deprecated
    @Column(name = "calibration_json", columnDefinition = "JSON")
    private String calibrationJson;

    @Column(name = "normalized_score", precision = 7, scale = 2)
    private BigDecimal normalizedScore;

    @Column(name = "goal_snapshot_json", columnDefinition = "TEXT")
    private String goalSnapshotJson;

    @Column(name = "confirmed_grade", length = 8)
    private String confirmedGrade;

    @Column(name = "final_score_snapshot", precision = 7, scale = 2)
    private BigDecimal finalScoreSnapshot;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // -----------------------------------------------------------------
    // v4 — 이의제기 / Lead 재할당 추적
    // -----------------------------------------------------------------

    /** NONE / REQUESTED / REVIEWING / RESOLVED — 평문 String 저장 (마이그레이션 호환) */
    @Column(name = "objection_status", length = 16)
    @Builder.Default
    private String objectionStatus = "NONE";

    @Column(name = "objection_message", columnDefinition = "TEXT")
    private String objectionMessage;

    @Column(name = "objection_resolution", columnDefinition = "TEXT")
    private String objectionResolution;

    @Column(name = "objection_requested_at")
    private LocalDateTime objectionRequestedAt;

    @Column(name = "objection_resolved_at")
    private LocalDateTime objectionResolvedAt;

    @Column(name = "objection_resolved_by")
    private UUID objectionResolvedBy;

    @Column(name = "lead_reassigned_at")
    private LocalDateTime leadReassignedAt;

    @Column(name = "lead_reassigned_by")
    private UUID leadReassignedBy;

    @Column(name = "lead_reassign_reason", columnDefinition = "TEXT")
    private String leadReassignReason;

    // -----------------------------------------------------------------
    // 단계 전이
    // -----------------------------------------------------------------

    public void saveSelfAnswers(String answersJson) {
        if (this.stage != EvaluationStage.SELF_PENDING) {
            throw new IllegalStateException("자기평가 입력 단계가 아님: " + this.stage);
        }
        this.answersJson = answersJson;
    }

    public void submitSelf(String answersJson) {
        if (this.stage != EvaluationStage.SELF_PENDING) {
            throw new IllegalStateException("자기평가 제출 가능 단계 아님: " + this.stage);
        }
        this.answersJson = answersJson;
        this.stage = EvaluationStage.SELF_SUBMITTED;
        this.status = EvaluationStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }

    /** 자기평가 마감 시 자동 호출 — 미제출 케이스 처리 */
    public void autoSubmitEmpty() {
        if (this.stage != EvaluationStage.SELF_PENDING) {
            return;
        }
        this.stage = EvaluationStage.SELF_SUBMITTED;
        this.status = EvaluationStatus.SUBMITTED;
        this.selfEvalEmpty = true;
        this.submittedAt = LocalDateTime.now();
    }

    public void openCalibration() {
        if (this.stage != EvaluationStage.SELF_SUBMITTED &&
            this.stage != EvaluationStage.PEER_OPEN &&
            this.stage != EvaluationStage.UPWARD_OPEN &&
            this.stage != EvaluationStage.DOWNWARD_OPEN) {
            throw new IllegalStateException("calibration 진입 불가 단계: " + this.stage);
        }
        this.stage = EvaluationStage.CALIBRATION_OPEN;
    }

    public void lockCalibration() {
        if (this.stage != EvaluationStage.CALIBRATION_OPEN) {
            throw new IllegalStateException("CALIBRATION_OPEN 에서만 lock 가능: " + this.stage);
        }
        this.stage = EvaluationStage.CALIBRATION_LOCKED;
    }

    public void confirm(UUID confirmedBy, String confirmedGrade, BigDecimal finalScore, BigDecimal normalizedScore) {
        if (this.stage != EvaluationStage.CALIBRATION_LOCKED &&
            this.stage != EvaluationStage.CALIBRATION_OPEN) {
            throw new IllegalStateException("calibration 단계 이후에만 확정 가능: " + this.stage);
        }
        this.stage = EvaluationStage.CONFIRMED;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = LocalDateTime.now();
        this.confirmedGrade = confirmedGrade;
        this.finalScoreSnapshot = finalScore;
        this.normalizedScore = normalizedScore;
    }

    public void refreshConfirmedOutcome(String confirmedGrade, BigDecimal finalScore, BigDecimal normalizedScore) {
        if (this.stage != EvaluationStage.CONFIRMED) {
            throw new IllegalStateException("CONFIRMED 응답만 재계산 가능: " + this.stage);
        }
        this.confirmedGrade = confirmedGrade;
        this.finalScoreSnapshot = finalScore;
        this.normalizedScore = normalizedScore;
    }

    /** 시즌 CLOSED 전이면 Lead 가 unconfirm 가능 → CALIBRATION_OPEN 으로 복귀 */
    public void unconfirm() {
        if (this.stage != EvaluationStage.CONFIRMED) {
            throw new IllegalStateException("CONFIRMED 에서만 되돌릴 수 있음: " + this.stage);
        }
        this.stage = EvaluationStage.CALIBRATION_OPEN;
        this.confirmedBy = null;
        this.confirmedAt = null;
        this.confirmedGrade = null;
        this.finalScoreSnapshot = null;
        this.normalizedScore = null;
    }

    public void skipForLeaver() {
        this.stage = EvaluationStage.SKIPPED_LEAVER;
    }

    /**
     * v4 — Lead 재할당 (HR 운영). 시즌 활성화 후 인사 변동에 대응.
     *  evaluatorId 변경 + 추적 메타데이터 기록.
     *  (LEAD calibration entry 의 evaluator_id 변경은 별도 서비스 책임)
     */
    public void reassignLead(UUID newLeadId, UUID actorId, String reason) {
        if (newLeadId == null) {
            throw new IllegalArgumentException("새 Lead 평가자 ID 필수");
        }
        if (this.stage == EvaluationStage.CONFIRMED || this.stage == EvaluationStage.SKIPPED_LEAVER) {
            throw new IllegalStateException("이미 종료된 응답은 Lead 재할당 불가: " + this.stage);
        }
        this.evaluatorId = newLeadId;
        this.leadReassignedAt = LocalDateTime.now();
        this.leadReassignedBy = actorId;
        this.leadReassignReason = reason;
    }

    /** v4 — 이의제기 요청 (본인) */
    public void requestObjection(String message) {
        if (this.stage != EvaluationStage.CONFIRMED) {
            throw new IllegalStateException("CONFIRMED 응답에 대해서만 이의제기 가능: " + this.stage);
        }
        this.objectionStatus = "REQUESTED";
        this.objectionMessage = message;
        this.objectionRequestedAt = LocalDateTime.now();
    }

    /** v4 — 이의제기 검토 시작 (HR) */
    public void reviewObjection() {
        if (!"REQUESTED".equals(this.objectionStatus)) {
            throw new IllegalStateException("REQUESTED 상태에서만 검토 진입 가능: " + this.objectionStatus);
        }
        this.objectionStatus = "REVIEWING";
    }

    /** v4 — 이의제기 종결 (HR) */
    public void resolveObjection(UUID resolverId, String resolution) {
        if (!"REQUESTED".equals(this.objectionStatus) && !"REVIEWING".equals(this.objectionStatus)) {
            throw new IllegalStateException("REQUESTED/REVIEWING 상태에서만 종결 가능: " + this.objectionStatus);
        }
        this.objectionStatus = "RESOLVED";
        this.objectionResolution = resolution;
        this.objectionResolvedBy = resolverId;
        this.objectionResolvedAt = LocalDateTime.now();
    }

    /**
     * 옵션 단계 진입 — scheduleJson 기반 자동 전이 시 호출.
     * 허용 전이: SELF_SUBMITTED -> (PEER/UPWARD/DOWNWARD)_OPEN
     */
    public void moveToOptionalStage(EvaluationStage nextStage) {
        if (this.stage != EvaluationStage.SELF_SUBMITTED) {
            throw new IllegalStateException("옵션 단계 진입 가능 단계 아님: " + this.stage);
        }
        if (nextStage != EvaluationStage.PEER_OPEN
                && nextStage != EvaluationStage.UPWARD_OPEN
                && nextStage != EvaluationStage.DOWNWARD_OPEN) {
            throw new IllegalArgumentException("허용되지 않은 옵션 단계: " + nextStage);
        }
        this.stage = nextStage;
    }

}
