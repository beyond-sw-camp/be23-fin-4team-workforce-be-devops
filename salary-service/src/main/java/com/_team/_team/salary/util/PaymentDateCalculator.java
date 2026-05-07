package com._team._team.salary.util;

import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.salary.domain.enums.PayDayShiftRule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 지급 예정일 계산, 주말/공휴일 자동 조정
 */
public final class PaymentDateCalculator {

    private PaymentDateCalculator() {}

    /**
     * targetMonth 의 payDay 일이 대상, 월 말일 초과 시 월 말일로 보정,
     * 주말/공휴일이면 rule 에 따라 직전/직후 영업일로 조정
     */
    public static LocalDate calculate(
            YearMonth targetMonth,
            int payDay,
            PayDayShiftRule rule,
            UUID companyId,
            CompanyHolidayRepository holidayRepository) {

        int day = Math.min(payDay, targetMonth.lengthOfMonth());
        LocalDate target = targetMonth.atDay(day);

        if (rule == null || rule == PayDayShiftRule.NONE) {
            return target;
        }

        int direction = (rule == PayDayShiftRule.BEFORE) ? -1 : 1;
        LocalDate cursor = target;
        int safety = 30;

        while (safety-- > 0 && isNonBusinessDay(cursor, companyId, holidayRepository)) {
            cursor = cursor.plusDays(direction);
        }
        return cursor;
    }

    private static boolean isNonBusinessDay(
            LocalDate date,
            UUID companyId,
            CompanyHolidayRepository holidayRepository) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return true;
        return holidayRepository.existsByCompanyIdAndHolidayDate(companyId, date);
    }
}