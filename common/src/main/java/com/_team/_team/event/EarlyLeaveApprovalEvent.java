package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 조퇴계 결재 처리 이벤트
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class EarlyLeaveApprovalEvent {

    public static final String TOPIC = "early-leave-approval";

    public enum Action { APPROVE, REJECT, CANCEL }

    private UUID companyId;
    private UUID memberId;
    private UUID requestId;

    // 조퇴 대상 일자
    private LocalDate attendanceDate;

    // 조퇴 시각 (참조용, 실제 lastClockOut 보정은 schedule.endTime 으로 대체)
    private LocalDateTime earlyLeaveAt;

    // 조퇴 사유
    private String reason;

    // 결재자
    private UUID approverId;

    // 결재 확정 시각
    private LocalDateTime decidedAt;

    // 반려/취소 코멘트 (APPROVE 면 null)
    private String note;

    private Action action;
}
