package com._team._team.salary.domain.enums;

/**
 * 보너스 일괄 발행 유형
 *  REGULAR     정기상여 (분기/반기/명절 포함 - 정책 연 누계율 / 지급 횟수 기준)
 *  PERFORMANCE 성과급 (평가 기반, 1회 최대 % 한도)
 *  HOLIDAY     명절상여 (RATE 또는 AMOUNT, 정책 holidayBonusType 기준)
 */
public enum BonusKind {
    REGULAR,
    PERFORMANCE,
    HOLIDAY
}
