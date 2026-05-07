package com._team._team.attendance.domain.enums;

/**
 * 법정 한도 위반 유형
 * WEEKLY_TOTAL_EXCEEDED       주 총 근로시간 초과 (법정 52시간)
 * WEEKLY_OVERTIME_EXCEEDED    주 연장근로 초과 (법정 12시간)
 * DAILY_OVERTIME_EXCEEDED     일 연장근로 초과 (회사 정책)
 * MONTHLY_OVERTIME_EXCEEDED   월 연장근로 초과 (회사 정책)
 * BREAK_TIME_INSUFFICIENT     법정 휴게시간 미달 (4h/30m, 8h/1h)
 */
public enum ViolationType {
    WEEKLY_TOTAL_EXCEEDED,
    WEEKLY_OVERTIME_EXCEEDED,
    DAILY_OVERTIME_EXCEEDED,
    MONTHLY_OVERTIME_EXCEEDED,
    BREAK_TIME_INSUFFICIENT
}
