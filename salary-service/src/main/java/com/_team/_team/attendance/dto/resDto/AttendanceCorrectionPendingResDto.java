package com._team._team.attendance.dto.resDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 정정 검토 큐 행 — 관리자 화면용
 * UNDER_REVIEW 상태 , 가장 최근 정정 로그(ADMIN_MANUAL)에서 추출한 사유/신청 시점
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class AttendanceCorrectionPendingResDto {

    private UUID dailyAttendanceId;
    private UUID memberId;
    private LocalDate attendanceDate;

    /** 직원이 신청한 출근 시각 (DA.firstClockIn) */
    private LocalDateTime requestedClockIn;
    /** 직원이 신청한 퇴근 시각 (DA.lastClockOut) */
    private LocalDateTime requestedClockOut;

    /** 정정 사유 (가장 최근 ADMIN_MANUAL 로그의 correctionReason) */
    private String reason;

    /** 신청 시점 (가장 최근 ADMIN_MANUAL 로그의 createdAt) */
    private LocalDateTime requestedAt;
}
