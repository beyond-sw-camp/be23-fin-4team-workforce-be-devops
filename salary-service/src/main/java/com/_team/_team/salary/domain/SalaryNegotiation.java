package com._team._team.salary.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.NegotiationStatus;
import com._team._team.salary.domain.enums.NegotiationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 연봉 협상 단건
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        indexes = {
                @Index(name = "idx_neg_company_member", columnList = "companyId, memberId"),
                @Index(name = "idx_neg_company_group",  columnList = "companyId, groupId"),
                @Index(name = "idx_neg_status",         columnList = "companyId, status")
        }
)
public class SalaryNegotiation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID negotiationId;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID memberId;

    /** 협상 종류 - 정기 / 승진 / 비정기  */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NegotiationType negotiationType;

    /** 묶음 ID 같은 시즌이면 동일 값 단건이면 null */
    private UUID groupId;

    /** 묶음 표시명 "2026 연봉 협상" 등 */
    @Column(length = 100)
    private String groupName;

    // 스냅샷
    @Column(nullable = false)
    private Long currentBaseSalary;

    @Column(length = 60)
    private String currentJobGradeName;

    @Column(length = 60)
    private String currentJobTitleName;

    // 협상안
    @Column(nullable = false)
    private Long proposedBaseSalary;

    @Column(length = 60)
    private String proposedJobGradeName;

    @Column(length = 60)
    private String proposedJobTitleName;

    @Column(nullable = false)
    private LocalDate proposedEffectiveFrom;

    /** 인상률 % 화면 표시용 캐시 (proposed-current)/current × 100 */
    @Column(nullable = false)
    @Builder.Default
    private Double changeRate = 0.0;

    @Column(length = 500)
    private String reason;

    // - 상태 -
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private NegotiationStatus status = NegotiationStatus.DRAFT;

    /** 결재 연계 옵션 자체 워크플로일 때 null */
    private UUID approvalRequestId;

    private LocalDateTime proposedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime appliedAt;

    private UUID decidedBy;

    @Column(length = 500)
    private String decisionNote;

    /** 적용 후 생성된 Salary FK */
    private UUID appliedSalaryId;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    // - 도메인 메서드 -

    /** 인상률 재계산 비교 분모 0 보호 */
    public void recalcChangeRate() {
        if (currentBaseSalary == null || currentBaseSalary == 0L) {
            this.changeRate = 0.0;
            return;
        }
        long diff = (proposedBaseSalary == null ? 0L : proposedBaseSalary) - currentBaseSalary;
        this.changeRate = (diff * 100.0) / currentBaseSalary;
    }

    /** DRAFT 상태에서 협상안 수정 */
    public void updateProposal(Long proposedBaseSalary,
                               String proposedJobGradeName,
                               String proposedJobTitleName,
                               LocalDate proposedEffectiveFrom,
                               String reason) {
        if (this.status != NegotiationStatus.DRAFT) {
            throw new IllegalStateException("DRAFT 상태에서만 수정 가능합니다.");
        }
        if (proposedBaseSalary != null) this.proposedBaseSalary = proposedBaseSalary;
        if (proposedJobGradeName != null) this.proposedJobGradeName = proposedJobGradeName;
        if (proposedJobTitleName != null) this.proposedJobTitleName = proposedJobTitleName;
        if (proposedEffectiveFrom != null) this.proposedEffectiveFrom = proposedEffectiveFrom;
        if (reason != null) this.reason = reason;
        recalcChangeRate();
    }

    /** DRAFT -> SUBMITTED 직원 통보 */
    public void submit() {
        if (this.status != NegotiationStatus.DRAFT) {
            throw new IllegalStateException("DRAFT 상태에서만 상신할 수 있습니다.");
        }
        this.status = NegotiationStatus.SUBMITTED;
        this.proposedAt = LocalDateTime.now();
    }

    /** SUBMITTED -> APPROVED 자체 워크플로 승인 */
    public void approve(UUID approverId, String note) {
        if (this.status != NegotiationStatus.SUBMITTED) {
            throw new IllegalStateException("상신된 건만 승인할 수 있습니다.");
        }
        this.status = NegotiationStatus.APPROVED;
        this.decidedAt = LocalDateTime.now();
        this.decidedBy = approverId;
        this.decisionNote = note;
    }

    /**  직원 거절 (사유 필수) */
    public void reject(UUID rejecterId, String reason) {
        if (this.status != NegotiationStatus.SUBMITTED) {
            throw new IllegalStateException("상신된 건만 거절할 수 있습니다.");
        }
        this.status = NegotiationStatus.REJECTED;
        this.decidedAt = LocalDateTime.now();
        this.decidedBy = rejecterId;
        this.decisionNote = reason;
    }

    /** APPROVED -> APPLIED 적용 */
    public void apply(UUID newSalaryId) {
        if (this.status != NegotiationStatus.APPROVED) {
            throw new IllegalStateException("승인된 건만 적용할 수 있습니다.");
        }
        this.status = NegotiationStatus.APPLIED;
        this.appliedAt = LocalDateTime.now();
        this.appliedSalaryId = newSalaryId;
    }

    /** 소프트 삭제 */
    public void softDelete() {
        this.delYn = "Y";
    }
}
