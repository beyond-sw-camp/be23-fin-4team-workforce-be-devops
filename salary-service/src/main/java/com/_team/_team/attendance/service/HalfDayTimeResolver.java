package com._team._team.attendance.service;

import com._team._team.attendance.domain.FlexibleTimeSlot;
import com._team._team.attendance.domain.MemberScheduleSelection;
import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.domain.enums.WorkType;
import com._team._team.attendance.repository.FlexibleTimeSlotRepository;
import com._team._team.attendance.repository.MemberScheduleSelectionRepository;
import com._team._team.attendance.repository.WorkScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 반차(HALF) 시각 산출
 * FIXED 근무: 회사 정한 출퇴근 시각 기반 점심 전/후로 분할
 * FLEXIBLE 근무: 그 직원 그 달 선택한 슬롯 기반 점심 전/후로 분할
 */
@Slf4j
@Service
public class HalfDayTimeResolver {

    public enum HalfType {
        AM,  // 오전 반차 - 출근시각 ~ 점심 직전
        PM   // 오후 반차 - 점심 직후 ~ 퇴근시각
    }

    private static final LocalTime DEFAULT_AM_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_AM_END   = LocalTime.of(13, 0);
    private static final LocalTime DEFAULT_PM_START = LocalTime.of(13, 0);
    private static final LocalTime DEFAULT_PM_END   = LocalTime.of(18, 0);
    private static final int DEFAULT_BREAK_MINUTES = 60;

    private final WorkScheduleRepository workScheduleRepository;
    private final FlexibleTimeSlotRepository flexibleTimeSlotRepository;
    private final MemberScheduleSelectionRepository selectionRepository;

    @Autowired
    public HalfDayTimeResolver(WorkScheduleRepository workScheduleRepository,
                               FlexibleTimeSlotRepository flexibleTimeSlotRepository,
                               MemberScheduleSelectionRepository selectionRepository) {
        this.workScheduleRepository = workScheduleRepository;
        this.flexibleTimeSlotRepository = flexibleTimeSlotRepository;
        this.selectionRepository = selectionRepository;
    }

    /**
     * 반차 시각 산출
     */
    public LocalTime[] resolve(UUID memberId, UUID companyId, LocalDate date, HalfType halfType) {
        // 1) 직원 개인 스케줄 우선, 없으면 회사 기본 스케줄
        WorkSchedule sched = workScheduleRepository
                .findEffectivePersonal(memberId, date)
                .orElseGet(() -> workScheduleRepository
                        .findEffectiveCompany(companyId, date)
                        .orElse(null));

        if (sched == null) {
            return defaultRange(halfType);
        }

        if (sched.getWorkType() == WorkType.FIXED) {
            return resolveFixed(sched, halfType);
        }
        if (sched.getWorkType() == WorkType.FLEXIBLE) {
            return resolveFlexible(memberId, date, halfType);
        }
        return defaultRange(halfType);
    }

    /**
     * 고정 근무제
     * 점심 직전 ~ 점심 직후로 오전/오후 분할
     */
    private LocalTime[] resolveFixed(WorkSchedule sched, HalfType halfType) {
        LocalTime start = sched.getStartTime();
        LocalTime end   = sched.getEndTime();
        if (start == null || end == null) {
            return defaultRange(halfType);
        }
        // 총 근무시간 - 점심 1h = 실 근무. 절반 = 4h 가정
        int workMin = (int) Duration.between(start, end).toMinutes() - DEFAULT_BREAK_MINUTES;
        int halfMin = workMin / 2;
        // 오전 끝 = 출근시각 + halfMin
        LocalTime amEnd = start.plusMinutes(halfMin);
        // 오후 시작 = 오전 끝 + 점심
        LocalTime pmStart = amEnd.plusMinutes(DEFAULT_BREAK_MINUTES);
        return halfType == HalfType.AM
                ? new LocalTime[]{ start, amEnd }
                : new LocalTime[]{ pmStart, end };
    }

    /**
     * 시차출퇴근제 - 그 직원이 그 달 선택한 FlexibleTimeSlot 기반
     */
    private LocalTime[] resolveFlexible(UUID memberId, LocalDate date, HalfType halfType) {
        String yearMonth = YearMonth.from(date).toString();
        MemberScheduleSelection selection = selectionRepository
                .findCurrentActive(memberId, yearMonth)
                .orElse(null);
        if (selection == null || selection.getSlotId() == null) {
            return defaultRange(halfType);
        }
        FlexibleTimeSlot slot = flexibleTimeSlotRepository
                .findById(selection.getSlotId())
                .orElse(null);
        if (slot == null || slot.getStartTime() == null || slot.getEndTime() == null) {
            return defaultRange(halfType);
        }
        // 슬롯 점심 시각이 명시되어 있으면 그대로 사용
        LocalTime breakStart = slot.getBreakStart();
        LocalTime breakEnd   = slot.getBreakEnd();
        if (breakStart == null || breakEnd == null) {
            // 점심 미정 - FIXED 와 동일 방식 분할
            int workMin = (int) Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes()
                    - DEFAULT_BREAK_MINUTES;
            int halfMin = workMin / 2;
            LocalTime amEnd = slot.getStartTime().plusMinutes(halfMin);
            LocalTime pmStart = amEnd.plusMinutes(DEFAULT_BREAK_MINUTES);
            return halfType == HalfType.AM
                    ? new LocalTime[]{ slot.getStartTime(), amEnd }
                    : new LocalTime[]{ pmStart, slot.getEndTime() };
        }
        // 점심 시각 정의 있음 - 그대로 사용
        return halfType == HalfType.AM
                ? new LocalTime[]{ slot.getStartTime(), breakStart }
                : new LocalTime[]{ breakEnd, slot.getEndTime() };
    }

    private LocalTime[] defaultRange(HalfType halfType) {
        return halfType == HalfType.AM
                ? new LocalTime[]{ DEFAULT_AM_START, DEFAULT_AM_END }
                : new LocalTime[]{ DEFAULT_PM_START, DEFAULT_PM_END };
    }
}
