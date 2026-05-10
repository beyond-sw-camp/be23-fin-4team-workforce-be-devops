package com._team._team.salary.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.AllowanceApprovalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 개인별 수당 적용 내역
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        indexes = {
                // 급여 계산 시 활성 수당 조회
                @Index(name = "idx_allowance_member_effective",
                        columnList = "memberId, effectiveFrom, effectiveTo"),
                // 관리자 결재 대기 목록
                @Index(name = "idx_allowance_company_status",
                        columnList = "companyId, approvalStatus")
        }
)
public class MemberAllowance  extends BaseTimeEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID memberAllowanceId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID companyId;

    // 어떤 수당인지, SalaryItemTemplate 참조
    @Column(nullable = false)
    private UUID salaryItemTemplateId;

    // 개인별 금액
    @Column(nullable = false)
    private Long amount;

    // 적용 시작일, 이 날 포함
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    // 적용 종료일, null 이면 현재 적용 중
    private LocalDate effectiveTo;

    // 결재 상태, AUTO 는 입사 시 기본 세팅 건
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllowanceApprovalStatus approvalStatus;

    // 변경 사유, 결재 시 필수
    @Column(length = 500)
    private String reason;

    // 신청자, AUTO 건은 SYSTEM UUID
    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    // approval-service 결재 UUID, AUTO 는 null
    private UUID approvalRequestId;

    private LocalDateTime decidedAt;
    private UUID decidedBy;

    @Column(length = 500)
    private String decisionNote;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    // 이전 수당 종료, 새 수당 발효 전날로 close
    public void closeEffectivePeriod(LocalDate endDate) {
        this.effectiveTo = endDate;
    }

    // 결재 승인 시점 회사 payDay 기준 적용시점 재산정
    public void rescheduleEffectiveFrom(LocalDate newEffectiveFrom) {
        if (this.approvalStatus != AllowanceApprovalStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 적용 시작일 재산정 가능합니다.");
        }
        this.effectiveFrom = newEffectiveFrom;
    }

    // 결재 승인 처리
    public void approve(UUID approverId, LocalDateTime decidedAt) {
        if (this.approvalStatus != AllowanceApprovalStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 승인 가능합니다.");
        }
        this.approvalStatus = AllowanceApprovalStatus.APPROVED;
        this.decidedBy = approverId;
        this.decidedAt = decidedAt;
    }

    // 결재 반려 처리
    public void reject(UUID approverId, LocalDateTime decidedAt, String note) {
        if (this.approvalStatus != AllowanceApprovalStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 반려 가능합니다.");
        }
        this.approvalStatus = AllowanceApprovalStatus.REJECTED;
        this.decidedBy = approverId;
        this.decidedAt = decidedAt;
        this.decisionNote = note;
    }

    // 본인 철회, PENDING 만 가능
    public void cancel() {
        if (this.approvalStatus != AllowanceApprovalStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 취소 가능합니다.");
        }
        this.approvalStatus = AllowanceApprovalStatus.CANCELLED;
    }

    // 결재 ID 연결
    public void linkApprovalRequest(UUID approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }

    public void softDelete() {
        this.delYn = "Y";
    }
}
