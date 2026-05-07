package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 수당 변경 결재 이벤트, approval-service -> salary-service
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class AllowanceApprovalEvent {

    public static final String TOPIC = "allowance-approval";

    private UUID companyId;
    private UUID memberId;
    private UUID requestId;
    // 승인자, 결재자 UUID
    private UUID approverId;
    private LocalDateTime decidedAt;
    // 반려 시 사유
    private String note;
    private Action action;

    public enum Action {
        APPROVE, REJECT, CANCEL
    }
}