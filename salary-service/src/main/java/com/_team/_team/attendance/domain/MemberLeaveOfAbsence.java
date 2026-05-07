package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.LeaveOfAbsenceApprovalStatus;
import com._team._team.attendance.domain.enums.LeaveOfAbsenceType;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 직원 휴직 기록
 * 자연 종료는 배치 처리, 조기 복직은 관리자 수동 API
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(indexes = {
        @Index(name = "idx_loa_member_active",
                columnList = "memberId, status, startDate, endDate"),
        @Index(name = "idx_loa_company_status",
                columnList = "companyId, status"),
        @Index(name = "idx_loa_approval_request",
                columnList = "approvalRequestId")
})
public class MemberLeaveOfAbsence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID leaveOfAbsenceId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeaveOfAbsenceType type;

    // 희망 시작일
    @Column(nullable = false)
    private LocalDate startDate;

    // 희망 종료일
    @Column(nullable = false)
    private LocalDate endDate;

    // 실제 종료일, 자연 종료 시 endDate 와 동일, 조기 복직 시 더 빠른 날짜
    @Column
    private LocalDate actualEndDate;

    // Y, 유급 휴직 (출산/육아/군복무 등)
    // N, 무급 휴직 (개인사유 등)
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String isPaidYn = "N";

    @Column(length = 500)
    private String reason;

    // 진단서, 출생증명서 등
    @Column(length = 500)
    private String evidenceFileUrl;

    // approval-service 결재 ID, 신청 직후엔 null, 링크 API 로 연결
    @Column
    private UUID approvalRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveOfAbsenceApprovalStatus status;

    // 신청자 (항상 본인)
    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    // 결재자, Kafka 수신 시 세팅
    @Column
    private UUID decidedBy;

    @Column
    private LocalDateTime decidedAt;

    @Column(length = 500)
    private String decisionNote;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    // 결재 ID 연결
    public void linkApprovalRequest(UUID approvalRequestId) {
        if (this.approvalRequestId != null) {
            throw new IllegalStateException("이미 결재가 연결된 휴직 신청입니다.");
        }
        this.approvalRequestId = approvalRequestId;
    }

    // 결재 승인 반영
    public void approve(UUID approverId, LocalDateTime decidedAt) {
        if (this.status != LeaveOfAbsenceApprovalStatus.REQUESTED) {
            throw new IllegalStateException("신청 대기 상태에서만 승인 가능합니다.");
        }
        this.status = LeaveOfAbsenceApprovalStatus.ACTIVE;
        this.decidedBy = approverId;
        this.decidedAt = decidedAt;
    }

    // 결재 반려 반영
    public void reject(UUID approverId, LocalDateTime decidedAt, String note) {
        if (this.status != LeaveOfAbsenceApprovalStatus.REQUESTED) {
            throw new IllegalStateException("신청 대기 상태에서만 반려 가능합니다.");
        }
        this.status = LeaveOfAbsenceApprovalStatus.REJECTED;
        this.decidedBy = approverId;
        this.decidedAt = decidedAt;
        this.decisionNote = note;
    }

    // 본인 철회
    public void cancel() {
        if (this.status != LeaveOfAbsenceApprovalStatus.REQUESTED) {
            throw new IllegalStateException("신청 대기 상태에서만 취소 가능합니다.");
        }
        this.status = LeaveOfAbsenceApprovalStatus.CANCELLED;
    }

    // 자연 종료 (배치) or 조기 복직 (관리자 수동)
    public void end(LocalDate actualEndDate) {
        if (this.status != LeaveOfAbsenceApprovalStatus.ACTIVE) {
            throw new IllegalStateException("휴직 중 상태에서만 종료 가능합니다.");
        }
        this.status = LeaveOfAbsenceApprovalStatus.ENDED;
        this.actualEndDate = actualEndDate;
    }

    public void softDelete() {
        this.delYn = "Y";
    }
}