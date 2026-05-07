package com._team._team.attendance.domain.enums;

/**
 * 일별 근태 상태
 * - daily_attendance.status에서 사용
 * - 휴가 승인 시 LEAVE/HALF로 자동 변경
 */

public enum AttendanceStatus {
    NORMAL,     // 정상 출근
    ABSENT,     // 결근
    LEAVE,      // 휴가 (종일)
    HALF        // 반차
}