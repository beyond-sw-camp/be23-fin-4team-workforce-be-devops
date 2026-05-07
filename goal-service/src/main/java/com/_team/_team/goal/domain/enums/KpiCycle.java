package com._team._team.goal.domain.enums;

/**
 * 목표 주기.
 * 기존 MONTHLY / ANYTIME 제거.
 * cycleKey 형식 예: 2026-Q2 / 2026-H1 / 2026 / 2026-Q2-PARTIAL
 */
public enum KpiCycle {
    QUARTERLY,
    HALF_YEARLY,
    YEARLY
}
