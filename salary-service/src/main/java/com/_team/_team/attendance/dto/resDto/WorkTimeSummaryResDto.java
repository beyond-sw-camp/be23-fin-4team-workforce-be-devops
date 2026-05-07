package com._team._team.attendance.dto.resDto;

import lombok.*;

import java.time.LocalDate;

/**
 * 직원 본인 주간 근무시간 요약
 * warningLevel 은 프론트가 percent 로 결정 (75% WARNING, 92% CRITICAL, 100% EXCEEDED)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class WorkTimeSummaryResDto {

    private LocalDate weekStart;   // 일요일
    private LocalDate weekEnd;     // 토요일

    // 주 총 근무시간 (DailyAttendance.workedMinutes 합, 연장근무 포함 실 근무분)
    private Integer totalWorkedMinutes;
    private Integer totalLimitMinutes;     // 법정 주 52시간 = 3120
    private Integer totalUsagePercent;

    // 주 연장근무 승인분
    private Integer overtimeApprovedMinutes;
    private Integer overtimeLimitMinutes;   // 법정 주 12시간 = 720
    private Integer overtimeUsagePercent;

    // 월 누적 연장근무 (현재 월의 1일 ~ 기준일까지 DailyAttendance.overtimeMinutes 합)
    private Integer monthlyOvertimeMinutes;
    private Integer monthlyOvertimeLimitMinutes;   // 회사 정책 monthlyOvertimeLimitMinutes (없으면 null)
    private Integer monthlyOvertimeUsagePercent;

    // 주휴수당 자격, 근로기준법 55조, 주 15시간 이상 개근 시 자격 O
    private Boolean weeklyHolidayEligible;
    private Integer weeklyHolidayMinRequiredMinutes;  // 법정 최소 900분(15h)
    private Integer weeklyAbsentDays;                 // 해당 주 결근일수
    private String weeklyHolidayReason;               // 자격 충족/불충족 사유
}