package com._team._team.attendance.dto.vo;

/**
 * 일별 근무 시간 분류 결과 (값 객체, DB 저장 x)
 *   totalPayableMinutes  지급 대상 총 분
 *   regularMinutes       정규 근무분 (스케줄 workMinutes 이내)
 *   overtimeMinutes      연장 근무분 (승인된 것만 반영)
 *   nightMinutes         야간 교차분 (22:00-06:00)
 *   holidayMinutes       휴일 근무분 (공휴일에만 채워짐)
 *   leaveMinutes         휴가 차감분
 *   lateMinutes          지각 시간
 *   earlyLeaveMinutes    조퇴 시간
 */
public record WorkTimeBreakdown(
        int totalPayableMinutes,
        int regularMinutes,
        int overtimeMinutes,
        int nightMinutes,
        int holidayMinutes,
        int leaveMinutes,
        int lateMinutes,
        int earlyLeaveMinutes

) {

    public static WorkTimeBreakdown empty() {
        return new WorkTimeBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
