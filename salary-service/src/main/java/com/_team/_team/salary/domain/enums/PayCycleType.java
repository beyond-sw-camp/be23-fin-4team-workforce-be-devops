package com._team._team.salary.domain.enums;

/**
 * 급여 지급 주기 유형 - 정산 대상 월과 지급일 관계
 * - CURRENT_MONTH (당월분): 해당 월 중에 지급
 * - PREVIOUS_MONTH (전월분): 다음 달에 지급
 */
public enum PayCycleType {
    CURRENT_MONTH,
    PREVIOUS_MONTH
}
