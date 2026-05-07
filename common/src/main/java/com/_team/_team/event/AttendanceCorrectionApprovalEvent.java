package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 근태 정정 결재 이벤트
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class AttendanceCorrectionApprovalEvent {

    public static final String TOPIC = "attendance-correction-approval";

    public enum Action { APPROVE, REJECT, CANCEL }

    private UUID companyId;
    private UUID memberId;
    private UUID requestId;

    // 정정 대상 일자
    private LocalDate attendanceDate;

    // 정정 출근 시각 (선택)
    private LocalDateTime requestedClockIn;

    // 정정 퇴근 시각 (선택)
    private LocalDateTime requestedClockOut;

    // 정정 사유
    private String reason;

    // 결재자
    private UUID approverId;

    // 결재 확정 시각
    private LocalDateTime decidedAt;

    // 반려/취소 사유 (APPROVE 액션엔 null)
    private String note;

    private Action action;
}
