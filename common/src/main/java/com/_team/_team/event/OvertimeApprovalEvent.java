package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 초과근무(연장/야간/휴일) 결재 이벤트
 * - 승인 시: APPROVE 이벤트 발행
 * - 반려/취소 시: REJECT / CANCEL 이벤트 발행
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class OvertimeApprovalEvent {

    public static final String TOPIC = "overtime-approval";

    private UUID companyId;
    private UUID memberId;
    private UUID requestId;       // 멱등성 보장용 (ApprovalRequest.requestId)
    private String workType;      // 연장 / 야간 / 휴일
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Action action;        // APPROVE / REJECT / CANCEL

    public enum Action {
        APPROVE, REJECT, CANCEL
    }
}