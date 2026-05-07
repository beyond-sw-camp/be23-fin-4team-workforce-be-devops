package com._team._team.salary.domain.enums;

// 입사 / 퇴사 / 기간변경 시 일할계산 방식
public enum ProrationMethod {
    // 월급 × (재직일수 / 해당월 일수) - 가장 일반적 (28~31일 분모)
    DAYS_IN_MONTH,
    // 월급 × (재직일수 / 30) - 통상임금 산정 시 표준 방식
    FIXED_30,
    // 월급 × (재직 소정근로일수 / 월 소정근로일수) - 시급제 환산식
    WORKING_DAYS
}
