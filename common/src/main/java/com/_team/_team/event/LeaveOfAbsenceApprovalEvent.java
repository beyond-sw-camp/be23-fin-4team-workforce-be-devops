package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 휴직 결재 이벤트
 * APPROVE: 승인, LeaveOfAbsence 상태 ACTIVE
 * REJECT: 반려, 상태 REJECTED
 * 잔고 차감 없음 (휴직은 연차와 별개)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class LeaveOfAbsenceApprovalEvent {

    public static final String TOPIC = "leave-of-absence-approval";

    // approval-service 결재 ID (Consumer 에서 approvalRequestId 로 조회)
    private UUID requestId;

    // salary-service LeaveOfAbsence ID (fallback 조회 및 보강용)
    private UUID leaveOfAbsenceId;

    private UUID companyId;
    private UUID memberId;

    // 결재자
    private UUID approverId;

    // 결재 확정 시각
    private LocalDateTime decidedAt;

    // 반려 사유 (APPROVE 시엔 null)
    private String note;

    private Action action;

    public enum Action {
        APPROVE, REJECT
    }
}