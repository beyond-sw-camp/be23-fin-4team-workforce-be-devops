package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.attendance.domain.enums.LeaveApprovalStatus;
import com._team._team.attendance.domain.enums.LeaveInitiator;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 휴가 신청 이력
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        indexes = {
                // 개인 이력 조회, 특정일 휴가 검색
                @Index(name = "idx_leave_member_date",
                        columnList = "memberId, startDate, endDate"),
                // 관리자 결재 대기 목록
                @Index(name = "idx_leave_company_status",
                        columnList = "companyId, approvalStatus"),
                // Kafka Consumer 가 결재 ID 로 역조회
                @Index(name = "idx_leave_approval_request",
                        columnList = "approvalRequestId")
        }
)
public class LeaveRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID leaveRequestId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID companyId;

    // 어떤 휴가인지, CompanyLeaveType로 확인
    @Column(nullable = false)
    private UUID companyLeaveTypeId;

    // 시작일
    @Column(nullable = false)
    private LocalDate startDate;

    // 종료일, 반차는 startDate 와 동일
    @Column(nullable = false)
    private LocalDate endDate;

    // 실제 사용 일수, 경조 3일이면 3.0 / 반차 0.5
    // startDate ~ endDate 와 CompanyLeaveType.daysPerUse 로 Service 에서 계산
    @Column(nullable = false)
    private Double usageDays;

    // 승인 시 실제 차감된 balance, null 이면 차감 없는 휴가(경조, 예비군, 공가 등)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BalanceType deductedBalanceType;

    // 분할 차감 이력 JSON, {"MONTHLY": 2.0, "ANNUAL": 1.0}
    @Column(columnDefinition = "TEXT")
    private String deductionsJson;

    // 변경 사유/휴가 사유, 결재 시 필수
    @Column(length = 500)
    private String reason;

    // 비연속 사용 날짜 JSON 배열 (예: ["2026-05-22","2026-05-29"])
    @Column(length = 500)
    private String plannedDatesJson;

    // 증빙 서류 URL, 병가/경조 등에서 필수
    @Column(length = 500)
    private String evidenceFileUrl;

    // approval-service 결재 UUID, 신청 직후엔 null
    @Column
    private UUID approvalRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveApprovalStatus approvalStatus;

    // 신청자, 항상 본인
    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    // 결재자, Kafka 이벤트 수신 시 세팅
    @Column
    private UUID decidedBy;

    @Column
    private LocalDateTime decidedAt;

    // 반려/취소 사유
    @Column(length = 500)
    private String decisionNote;

    /** 신청 주체 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private LeaveInitiator initiator = LeaveInitiator.SELF;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    // 결재 ID 연결
    public void linkApprovalRequest(UUID approvalRequestId) {
        if (this.approvalRequestId != null) {
            throw new IllegalStateException("이미 결재가 연결된 휴가 신청입니다.");
        }
        this.approvalRequestId = approvalRequestId;
    }

    // 결재 승인 반영, Consumer 호출
    public void approve(UUID approverId, LocalDateTime decidedAt,
                        BalanceType deductedBalanceType,
                        String deductionsJson) {
        if (this.approvalStatus != LeaveApprovalStatus.PENDING) {
            throw new IllegalStateException("결재대기 상태에서만 승인 가능합니다.");
        }
        this.approvalStatus = LeaveApprovalStatus.APPROVED;
        this.decidedBy = approverId;
        this.decidedAt = decidedAt;
        this.deductedBalanceType = deductedBalanceType;
        this.deductionsJson = deductionsJson;
    }

    // 결재 반려 반영, Consumer 호출
    public void reject(UUID approverId, LocalDateTime decidedAt, String note) {
        if (this.approvalStatus != LeaveApprovalStatus.PENDING) {
            throw new IllegalStateException("결재대기 상태에서만 반려 가능합니다.");
        }
        this.approvalStatus = LeaveApprovalStatus.REJECTED;
        this.decidedBy = approverId;
        this.decidedAt = decidedAt;
        this.decisionNote = note;
    }

    // 본인 철회 (관리자 강제 지정은 차단)
    public void cancel() {
        if (this.initiator == LeaveInitiator.ADMIN_DESIGNATION) {
            throw new IllegalStateException(
                    "회사가 강제 지정한 연차는 취소할 수 없습니다. 관리자에게 문의하세요.");
        }
        if (this.approvalStatus != LeaveApprovalStatus.PENDING) {
            throw new IllegalStateException("결재대기 상태에서만 취소 가능합니다.");
        }
        this.approvalStatus = LeaveApprovalStatus.CANCELLED;
    }

    /**
     * 회사 강제 지정용
     */
    public static LeaveRequest createDesignated(UUID memberId,
                                                UUID companyId,
                                                UUID companyLeaveTypeId,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                Double usageDays,
                                                String reason,
                                                UUID adminId) {
        LeaveRequest req = LeaveRequest.builder()
                .memberId(memberId)
                .companyId(companyId)
                .companyLeaveTypeId(companyLeaveTypeId)
                .startDate(startDate)
                .endDate(endDate)
                .usageDays(usageDays)
                .reason(reason)
                .approvalStatus(LeaveApprovalStatus.APPROVED)
                .initiator(LeaveInitiator.ADMIN_DESIGNATION)
                .requestedBy(adminId)
                .requestedAt(LocalDateTime.now())
                .decidedBy(adminId)
                .decidedAt(LocalDateTime.now())
                .build();
        return req;
    }

    public void softDelete() {
        this.delYn = "Y";
    }
}