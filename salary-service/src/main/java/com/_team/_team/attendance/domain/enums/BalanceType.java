package com._team._team.attendance.domain.enums;
/**
 * 휴가 잔여 유형
 * - ANNUAL: 당해 연차
 * - MONTHLY: 입사 1년 미만 월 단위 부여 (법정 월차에 해당)
 * - CARRYOVER: 전년도 이월 연차 (leave_policy.isCarryoverYn에 따라 발생)
 */
public enum BalanceType {
    ANNUAL,
    MONTHLY,
    CARRYOVER
}