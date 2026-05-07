package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 근태 정정 결재 상신 이벤트
 * 직원이 결재를 상신한 시점에 1회 발행, 일일근태를 UNDER_REVIEW 로 격리하고 정정 로그 추가
 * 결재가 진행 중인 동안 02:00/14:00 마감 배치가 그 행을 건드리지 않게 보호
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class AttendanceCorrectionSubmittedEvent {

    public static final String TOPIC = "attendance-correction-submitted";

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

    // 신청자 (보통 memberId 와 동일)
    private UUID submittedBy;

    // 신청 시각
    private LocalDateTime submittedAt;
}
