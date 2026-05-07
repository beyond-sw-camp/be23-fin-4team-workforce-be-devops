package com._team._team.salary.domain.enums;

/**
 * 지급일이 주말/공휴일인 경우 조정 규칙
 */
public enum PayDayShiftRule {
    /** 해당 일에 그대로 지급 */
    NONE,
    /** 직전 영업일로 앞당김 (실무 표준) */
    BEFORE,
    /** 직후 영업일로 미룸 */
    AFTER
}