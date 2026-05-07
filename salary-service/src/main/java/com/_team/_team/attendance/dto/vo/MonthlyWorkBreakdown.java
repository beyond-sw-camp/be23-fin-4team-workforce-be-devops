package com._team._team.attendance.dto.vo;

/**
 * 월 근무 시간 세부 내역 (값 객체)
 * DB 저장 안함, 메서드 간 전달용
 */
public record MonthlyWorkBreakdown(
        int totalWorkedMinutes,
        int regularMinutes,
        int overtimeMinutes,
        int nightMinutes,
        int holidayMinutes,
        int leaveMinutes,
        int lateMinutes,
        int earlyLeaveMinutes,
        int absentDays,
        String leaveBreakdownJson
) {

    // 빈 객체 생성
    public static MonthlyWorkBreakdown empty() {
        return new MonthlyWorkBreakdown(0, 0, 0, 0, 0, 0, 0, 0, 0, "{}");
    }

    // 일별 결과 누적
    public MonthlyWorkBreakdown add(WorkTimeBreakdown daily) {
        return new MonthlyWorkBreakdown(
                totalWorkedMinutes + daily.totalPayableMinutes(),
                regularMinutes + daily.regularMinutes(),
                overtimeMinutes + daily.overtimeMinutes(),
                nightMinutes + daily.nightMinutes(),
                holidayMinutes + daily.holidayMinutes(),
                leaveMinutes + daily.leaveMinutes(),
                lateMinutes + daily.lateMinutes(),
                earlyLeaveMinutes + daily.earlyLeaveMinutes(),
                absentDays,
                leaveBreakdownJson
        );
    }

    // 결근 일수 별도 증가
    public MonthlyWorkBreakdown incrementAbsent() {
        return new MonthlyWorkBreakdown(
                totalWorkedMinutes, regularMinutes, overtimeMinutes,
                nightMinutes, holidayMinutes, leaveMinutes,
                lateMinutes, earlyLeaveMinutes,
                absentDays + 1,
                leaveBreakdownJson
        );
    }
}