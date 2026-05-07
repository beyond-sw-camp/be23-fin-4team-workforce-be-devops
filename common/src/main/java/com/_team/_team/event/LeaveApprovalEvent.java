package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 휴가 결재 이벤트 - approval-service -> salary-service
 * USE: 승인됨 (잔고 차감 + LeaveRequest 상태 APPROVED)
 * REJECT: 반려됨 (잔고 불변 + LeaveRequest 상태 REJECTED)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class LeaveApprovalEvent {

    public static final String TOPIC = "leave-approval";

    private UUID requestId;
    private UUID leaveRequestId;
    private UUID companyId;
    private UUID memberId;

    // 차감 필요 여부 (연차/반차 = true, 경조/공가 = false)
    private Boolean needsDeduction;

    // 사용 일수
    private Double days;

    // 휴가 시작일 (하루짜리 반차/연차는 leaveDate 와 동일)
    private LocalDate leaveDate;

    // 결재자
    private UUID approverId;

    // 결재 확정 시각
    private LocalDateTime decidedAt;

    // 반려 사유 (USE 액션엔 null)
    private String note;

    private Action action;

    public enum Action {
        USE,    // 승인 (잔고 차감)
        REJECT  // 반려 (잔고 불변)
    }
}
