package com._team._team.salary.domain;

import com._team._team.salary.domain.enums.PayCycleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 한국 급여 관행 - 당월분(CURRENT_MONTH) vs 전월분(PREVIOUS_MONTH) 분기
 */
class SalaryPolicyTargetYearMonthTest {

    private SalaryPolicy buildPolicy(PayCycleType cycle) {
        return SalaryPolicy.builder()
                .companyId(UUID.randomUUID())
                .policyName("test")
                .payDay(25)
                .payCycleType(cycle)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Test
    @DisplayName("CURRENT_MONTH - 5월 25일 지급 -> 5월분")
    void currentMonth() {
        var policy = buildPolicy(PayCycleType.CURRENT_MONTH);
        var ym = policy.resolveTargetYearMonth(LocalDate.of(2026, 5, 25));
        assertEquals(YearMonth.of(2026, 5), ym);
    }

    @Test
    @DisplayName("PREVIOUS_MONTH - 6월 10일 지급 -> 5월분")
    void previousMonth() {
        var policy = buildPolicy(PayCycleType.PREVIOUS_MONTH);
        var ym = policy.resolveTargetYearMonth(LocalDate.of(2026, 6, 10));
        assertEquals(YearMonth.of(2026, 5), ym);
    }

    @Test
    @DisplayName("PREVIOUS_MONTH - 1월 10일 지급 -> 전년 12월분 (연도 경계)")
    void previousMonthYearBoundary() {
        var policy = buildPolicy(PayCycleType.PREVIOUS_MONTH);
        var ym = policy.resolveTargetYearMonth(LocalDate.of(2026, 1, 10));
        assertEquals(YearMonth.of(2025, 12), ym);
    }

    @Test
    @DisplayName("CURRENT_MONTH - 12월 25일 지급 -> 12월분 (연말)")
    void currentMonthYearEnd() {
        var policy = buildPolicy(PayCycleType.CURRENT_MONTH);
        var ym = policy.resolveTargetYearMonth(LocalDate.of(2026, 12, 25));
        assertEquals(YearMonth.of(2026, 12), ym);
    }
}
