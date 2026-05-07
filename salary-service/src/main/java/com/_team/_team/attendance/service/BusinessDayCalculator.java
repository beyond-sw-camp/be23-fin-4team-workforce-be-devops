package com._team._team.attendance.service;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 영업일 계산, 주말 + 공휴일 제외
 */
public class BusinessDayCalculator {

    private BusinessDayCalculator() {}

    /**
     * 시작일 ~ 종료일 중 영업일 수 반환
     */
    public static int countBusinessDays(LocalDate start, LocalDate end,
                                        Set<LocalDate> holidays) {
        int count = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (isBusinessDay(date, holidays)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }
}