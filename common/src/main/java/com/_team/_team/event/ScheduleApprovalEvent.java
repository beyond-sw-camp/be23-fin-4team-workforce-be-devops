package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 스케줄 스케줄 선택 결재 완료 이벤트
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class ScheduleApprovalEvent {

    public static final String TOPIC = "schedule-approval";

    private UUID companyId;
    private UUID memberId;
    private UUID requestId;            // 멱등성 보장용
    private UUID selectionId;
    private UUID approverId;
    private LocalDateTime decidedAt;
    private String note;               // 반려 코멘트
    private Action action;             // APPROVE / REJECT / CANCEL


    public enum Action {
        APPROVE, REJECT, CANCEL
    }
}

