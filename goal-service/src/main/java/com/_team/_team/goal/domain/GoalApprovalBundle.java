package com._team._team.goal.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.goal.domain.converter.UuidListConverter;
import com._team._team.goal.domain.enums.BundleApprovalStatus;
import com._team._team.goal.domain.enums.GoalApprovalDecision;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GoalApprovalBundle (재정의)
 *
 *  - BundleApprovalKind (ACTIVATION/COMPLETION) 폐기 → 단일 ACTIVATION 모델
 *  - cycleKey: "2026-Q2", "2026-H1", "2026", "2026-Q2-PARTIAL" 형식
 *  - 같은 (requestedBy, cycleKey) 에 PENDING 1건만 허용 (앱 레벨 검증)
 *  - 반려 후 재상신은 새 bundle 생성 + originalBundleId 로 체인 연결
 */
@Entity
@Table(name = "goal_approval_bundle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GoalApprovalBundle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bundle_id")
    private UUID bundleId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** "2026-Q2" 형태. 100% 가중치 검증의 기본 키 */
    @Column(name = "cycle_key", length = 32, nullable = false)
    private String cycleKey;

    /** 재상신 횟수 (1 시작) */
    @Column(name = "revision", nullable = false)
    @Builder.Default
    private int revision = 1;

    /** 재상신 체인 — 직전 반려 bundle 의 ID */
    @Column(name = "original_bundle_id")
    private UUID originalBundleId;

    /** 제출 시점의 가중치 합 스냅샷 — 정수% (정상 = 100) */
    @Column(name = "weight_sum_snapshot", nullable = false)
    @Builder.Default
    private int weightSumSnapshot = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private BundleApprovalStatus status = BundleApprovalStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    @Builder.Default
    private GoalApprovalDecision decision = GoalApprovalDecision.PENDING;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "comment_text", length = 1000)
    private String commentText;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Column(name = "last_rejected_reason", length = 2000)
    private String lastRejectedReason;

    /** 이 bundle 에 포함된 goal 들 — 일괄 전이 대상 */
    @Convert(converter = UuidListConverter.class)
    @Column(name = "goal_ids_json", columnDefinition = "TEXT")
    @Builder.Default
    private List<UUID> goalIds = new ArrayList<>();

    /** 권한자가 코멘트로 가리킨 문제 goal 들 */
    @Convert(converter = UuidListConverter.class)
    @Column(name = "affected_goal_ids_json", columnDefinition = "TEXT")
    @Builder.Default
    private List<UUID> affectedGoalIds = new ArrayList<>();

    @Convert(converter = UuidListConverter.class)
    @Column(name = "watcher_ids_json", columnDefinition = "TEXT")
    @Builder.Default
    private List<UUID> watcherIds = new ArrayList<>();

    // -----------------------------------------------------------------
    public void approve(String commentText) {
        if (this.status != BundleApprovalStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 승인 가능: " + this.status);
        }
        this.status = BundleApprovalStatus.APPROVED;
        this.decision = GoalApprovalDecision.APPROVED;
        this.commentText = commentText;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(String reason, List<UUID> affectedGoalIds) {
        if (this.status != BundleApprovalStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 반려 가능: " + this.status);
        }
        this.status = BundleApprovalStatus.REJECTED;
        this.decision = GoalApprovalDecision.REJECTED;
        this.rejectionReason = reason;
        this.lastRejectedReason = reason;
        this.affectedGoalIds = affectedGoalIds == null ? new ArrayList<>() : new ArrayList<>(affectedGoalIds);
        this.decidedAt = LocalDateTime.now();
    }

    public void withdraw() {
        if (this.status != BundleApprovalStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 회수 가능: " + this.status);
        }
        this.status = BundleApprovalStatus.WITHDRAWN;
        this.decision = GoalApprovalDecision.PENDING;
        this.decidedAt = LocalDateTime.now();
    }

}
