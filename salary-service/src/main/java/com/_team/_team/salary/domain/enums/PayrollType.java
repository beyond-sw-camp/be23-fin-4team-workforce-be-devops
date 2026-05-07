package com._team._team.salary.domain.enums;

/**
 * 급여대장 구분
 *  REGULAR_MONTHLY        정기급여 (배치로 매월 자동 생성)
 *  PERFORMANCE_BONUS      성과급
 *  SPECIAL_BONUS          특별상여 (명절·차례비·창립기념일 등)
 *  RETROACTIVE            소급분 (과거 인상분 일괄 정산 등)
 *  RETIREMENT_SETTLEMENT  퇴직정산 (사직 결재 승인 시 자동 생성)
 */
public enum PayrollType {
    REGULAR_MONTHLY,
    PERFORMANCE_BONUS,
    SPECIAL_BONUS,
    RETROACTIVE,
    RETIREMENT_SETTLEMENT
}
