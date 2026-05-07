package com._team._team.attendance.service;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.dto.vo.ResolvedSchedule;
import com._team._team.attendance.dto.vo.WorkTimeBreakdown;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 근무 시간 분류 엔진
 * 하루 근태를 정규/연장/야간/휴일/휴가 로 분해
 */
@Service
public class WorkTimeClassifier {

    // 야간 근로 기본 시간대
    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    /**
     * 하루 근태를 분류해서 WorkTimeBreakdown 으로 반환
     */
    public WorkTimeBreakdown classify(DailyAttendance daily,
                                      ResolvedSchedule schedule,
                                      boolean isHoliday,
                                      int approvedOvertimeMinutes,
                                      int overtimeFloorMinutes) {

        // 출퇴근 기록 없음 (휴가 또는 결근)
        if (daily.getFirstClockIn() == null || daily.getLastClockOut() == null) {
            return handleNoClockInOut(daily, schedule);
        }

        // 전일 휴가(LEAVE) 확정일 출근은 무효 처리, 휴가 차감만 적용
        // 반차(HALF)는 일부 출근이 정상 케이스이므로 아래 평일 분류로 진행
        if (daily.getStatus() == AttendanceStatus.LEAVE) {
            return new WorkTimeBreakdown(
                    0, 0, 0, 0, 0, schedule.workMinutes(), 0, 0
            );
        }

        // 1. 체류 시간 계산
        int stayMinutes = (int) Duration.between(
                daily.getFirstClockIn(),
                daily.getLastClockOut()
        ).toMinutes();

        // 2. 휴게 차감 - 실제 체류 구간과 휴게 구간의 교집합만 차감
        //  체류시간에 휴게 시간을 포함 안 하면 차감 0 (예: 17:33-18:00 출근시 점심 12-13 차감 X)
        int breakOverlap = computeBreakOverlap(daily, schedule);
        int payable = Math.max(0, stayMinutes - breakOverlap);

        // 3. 야간 교차분 계산 (공휴일 여부 무관)
        int night = calculateNightMinutes(
                daily.getFirstClockIn(),
                daily.getLastClockOut()
        );
        // 4. 공휴일 근무는 전부 holidayMinutes 로
        if (isHoliday) {
            return new WorkTimeBreakdown(
                    payable, 0, 0, night, payable, 0, 0, 0
            );
        }

        // 5. 평일 근무 분류 (반차여도 8시간 초과분만 overtime)
        int regular = Math.min(payable, schedule.workMinutes());
        int actualOt = Math.max(0, payable - schedule.workMinutes());
        int overtime = Math.min(actualOt, approvedOvertimeMinutes);

        // 내림 처리, 단위 미만 시간은 인정하지 않음 (예: 단위 15분, 근무 14분 -> 0분 / 근무 29분 -> 15분)
        overtime = floorRound(overtime, overtimeFloorMinutes);

        int late = calculateLateMinutes(daily, schedule);
        int early = calculateEarlyLeaveMinutes(daily, schedule);
        int leave = calculateLeaveMinutes(daily, schedule);

        // 조퇴계 결재 승인된 날
        // (휴가 차감 X, 정규 8h 유지) 조퇴 분(early) 도 무효 처리하고 payable 에도 정규시간 반영
        if (daily.isEarlyLeaveExcused()) {
            regular = schedule.workMinutes();
            early = 0;
            // payable 표시는 실제 체류시간 기준 유지
            payable = Math.max(payable, schedule.workMinutes());
        }

        return new WorkTimeBreakdown(
                payable, regular, overtime, night, 0, leave, late, early
        );
    }

    // 출퇴근 기록이 없는 경우 상태 기반 처리
    private WorkTimeBreakdown handleNoClockInOut(DailyAttendance daily,
                                                 ResolvedSchedule schedule) {
        if (daily.getStatus() == AttendanceStatus.LEAVE) {
            // 종일 휴가
            return new WorkTimeBreakdown(
                    0, 0, 0, 0, 0, schedule.workMinutes(), 0, 0
            );
        }
        // 결근 또는 미기록
        return WorkTimeBreakdown.empty();
    }

    // 체류 구간과 휴게 구간의 교집합 계산
    // breakStart/End 가 있으면 그 시간대와 체류 시간 겹친 분만 차감
    // 시각 정보 없으면 fallback - schedule.breakMinutes() 단순 차감 (옛 동작 호환)
    private int computeBreakOverlap(DailyAttendance daily, ResolvedSchedule schedule) {
        int fallback = schedule.breakMinutes() != null ? schedule.breakMinutes() : 0;
        if (schedule.breakStart() == null || schedule.breakEnd() == null) {
            return fallback;
        }
        LocalDate startDate = daily.getFirstClockIn().toLocalDate();
        LocalDateTime breakStart = startDate.atTime(schedule.breakStart());
        LocalDateTime breakEnd = startDate.atTime(schedule.breakEnd());
        // 휴게 종료가 시작보다 빠르면 익일로 넘어가는 케이스 보정
        if (!breakEnd.isAfter(breakStart)) {
            breakEnd = breakEnd.plusDays(1);
        }
        return overlapMinutes(
                daily.getFirstClockIn(), daily.getLastClockOut(),
                breakStart, breakEnd);
    }

    // 체류 구간과 야간 시간대(22:00-06:00)의 교집합 계산
    private int calculateNightMinutes(LocalDateTime clockIn, LocalDateTime clockOut) {
        LocalDate startDate = clockIn.toLocalDate();
        int total = 0;

        // 당일 22:00 ~ 24:00 구간
        LocalDateTime nightA_start = startDate.atTime(NIGHT_START);
        LocalDateTime nightA_end = startDate.plusDays(1).atStartOfDay();
        total += overlapMinutes(clockIn, clockOut, nightA_start, nightA_end);

        // 익일 00:00 ~ 06:00 구간
        LocalDateTime nightB_start = startDate.plusDays(1).atStartOfDay();
        LocalDateTime nightB_end = startDate.plusDays(1).atTime(NIGHT_END);
        total += overlapMinutes(clockIn, clockOut, nightB_start, nightB_end);

        return total;
    }

    // 두 시간 구간의 겹치는 분 수 (안 겹치면 0)
    private int overlapMinutes(LocalDateTime aStart, LocalDateTime aEnd,
                               LocalDateTime bStart, LocalDateTime bEnd) {
        LocalDateTime start = aStart.isAfter(bStart) ? aStart : bStart;
        LocalDateTime end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        if (start.isBefore(end)) {
            return (int) Duration.between(start, end).toMinutes();
        }
        return 0;
    }

    // 지각 분 계산 (firstClockIn 이 스케줄 시작보다 늦은 경우)
    private int calculateLateMinutes(DailyAttendance daily, ResolvedSchedule schedule) {
        LocalDateTime scheduledStart = daily.getAttendanceDate()
                .atTime(schedule.startTime());
        if (daily.getFirstClockIn().isAfter(scheduledStart)) {
            return (int) Duration.between(
                    scheduledStart,
                    daily.getFirstClockIn()
            ).toMinutes();
        }
        return 0;
    }

    // 조퇴 분 계산 (lastClockOut 이 스케줄 종료보다 이른 경우)
    private int calculateEarlyLeaveMinutes(DailyAttendance daily, ResolvedSchedule schedule) {
        LocalDateTime scheduledEnd = daily.getAttendanceDate()
                .atTime(schedule.endTime());
        if (daily.getLastClockOut().isBefore(scheduledEnd)) {
            return (int) Duration.between(
                    daily.getLastClockOut(),
                    scheduledEnd
            ).toMinutes();
        }
        return 0;
    }

    // 휴가 차감분 계산 (상태 기반)
    private int calculateLeaveMinutes(DailyAttendance daily, ResolvedSchedule schedule) {
        if (daily.getStatus() == AttendanceStatus.LEAVE) {
            return schedule.workMinutes();
        }
        if (daily.getStatus() == AttendanceStatus.HALF) {
            return schedule.workMinutes() / 2;
        }
        return 0;
    }

    // FLOOR 라운딩, 단위는 15 또는 30
    private int floorRound(int minutes, int unit) {
        if (minutes <= 0) return 0;
        return (minutes / unit) * unit;
    }
}