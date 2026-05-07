package com._team._team.attendance.dto.vo;

import com._team._team.attendance.domain.enums.WorkType;

import java.time.LocalTime;
import java.util.UUID;

/**
 * 스케줄 해석 결과 (값 객체, DB 저장 X)
 * 한 직원의 특정 일자에 적용되는 최종 근무, 휴게 정보를 담음
 */
public record ResolvedSchedule(
        UUID workScheduleId,
        UUID flexibleSlotId,     // FIXED 면 null
        WorkType workType,
        LocalTime startTime,
        LocalTime endTime,
        Integer workMinutes,
        Integer breakMinutes,
        LocalTime breakStart,    // 휴게 시작 시각 (점심 등)
        LocalTime breakEnd       // 휴게 종료 시각
) {

    // FIXED 스케줄에서 조립 - 회사 정책 휴게시간 사용
    public static ResolvedSchedule fromFixed(
            UUID workScheduleId,
            LocalTime startTime,
            LocalTime endTime,
            Integer workMinutes,
            Integer breakMinutes,
            LocalTime breakStart,
            LocalTime breakEnd) {
        return new ResolvedSchedule(
                workScheduleId,
                null,
                WorkType.FIXED,
                startTime,
                endTime,
                workMinutes,
                breakMinutes,
                breakStart,
                breakEnd
        );
    }

    // 유연근무제(시차출퇴근 스케줄) + 선택한 스케줄 + 직원 선택 휴게시간으로 조립
    public static ResolvedSchedule fromFlexible(
            UUID workScheduleId,
            UUID flexibleSlotId,
            LocalTime startTime,
            LocalTime endTime,
            Integer workMinutes,
            Integer breakMinutes,
            LocalTime breakStart,
            LocalTime breakEnd) {
        return new ResolvedSchedule(
                workScheduleId,
                flexibleSlotId,
                WorkType.FLEXIBLE,
                startTime,
                endTime,
                workMinutes,
                breakMinutes,
                breakStart,
                breakEnd
        );
    }
}
