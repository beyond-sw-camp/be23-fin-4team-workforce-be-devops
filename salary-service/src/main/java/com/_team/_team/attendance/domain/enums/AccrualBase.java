package com._team._team.attendance.domain.enums;

/**
 * 연차 발생 기준
 * - FISCAL: 회계연도 기준 (1/1 ~ 12/31)
 * - HIRE_DATE: 입사일 기준 (입사 1년 후부터 15일 등)
 */
public enum AccrualBase {
    FISCAL,
    HIRE_DATE;
}
