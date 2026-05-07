package com._team._team.goal.util;

import com._team._team.goal.domain.enums.KpiCycle;

import java.time.LocalDate;
import java.time.Month;

/**
 * cycle + cycleStartDate → cycleKey 문자열 변환.
 *
 *  형식 예:
 *    QUARTERLY   + 2026-04-01 → "2026-Q2"
 *    QUARTERLY   + 2026-04-15 (중간입사 부분 cycle) → "2026-Q2-PARTIAL"
 *    HALF_YEARLY + 2026-01-01 → "2026-H1"
 *    HALF_YEARLY + 2026-07-01 → "2026-H2"
 *    YEARLY      + 2026-01-01 → "2026"
 */
public final class CycleKeyResolver {

    private CycleKeyResolver() {}

    /** cycle + 시작일이 정규(분기/반기/연간 첫날) 이면 표준 키, 아니면 -PARTIAL 접미사 */
    public static String resolve(KpiCycle cycle, LocalDate start) {
        if (cycle == null || start == null) {
            throw new IllegalArgumentException("cycle/start 필수");
        }
        boolean partial = !isCanonicalStart(cycle, start);
        String base = canonical(cycle, start);
        return partial ? base + "-PARTIAL" : base;
    }

    /** 같은 cycle 분류로 정규화된 cycleKey (PARTIAL 무시) — 가중치 묶음 키로 사용 */
    public static String resolveCanonical(KpiCycle cycle, LocalDate start) {
        return canonical(cycle, start);
    }

    private static String canonical(KpiCycle cycle, LocalDate start) {
        int year = start.getYear();
        switch (cycle) {
            case QUARTERLY:
                int quarter = ((start.getMonthValue() - 1) / 3) + 1;
                return year + "-Q" + quarter;
            case HALF_YEARLY:
                int half = (start.getMonthValue() <= 6) ? 1 : 2;
                return year + "-H" + half;
            case YEARLY:
                return String.valueOf(year);
            default:
                throw new IllegalArgumentException("unsupported cycle: " + cycle);
        }
    }

    private static boolean isCanonicalStart(KpiCycle cycle, LocalDate start) {
        if (start.getDayOfMonth() != 1) {
            return false;
        }
        Month m = start.getMonth();
        switch (cycle) {
            case QUARTERLY:
                return m == Month.JANUARY || m == Month.APRIL ||
                       m == Month.JULY    || m == Month.OCTOBER;
            case HALF_YEARLY:
                return m == Month.JANUARY || m == Month.JULY;
            case YEARLY:
                return m == Month.JANUARY;
            default:
                return false;
        }
    }
}
