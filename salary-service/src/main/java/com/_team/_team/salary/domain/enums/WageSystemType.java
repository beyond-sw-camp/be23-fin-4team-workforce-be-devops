package com._team._team.salary.domain.enums;

/**
 * 임금제 유형
 * - COMPREHENSIVE (포괄임금제):
 *     기본급 안에 일정 시간의 초과근무수당이 이미 포함되어 있는 형태.
 *     예) "월 고정 OT 20시간 포함" → 한 달 OT가 20시간 이내면 추가 지급 없음.
 *
 * - NON_COMPREHENSIVE (비포괄임금제):
 *     기본급에 OT가 포함되지 않음. 모든 초과근무에 대해 통상시급의 1.5배를 지급.
 *     근로기준법 제56조의 원칙적 형태
 */
public enum WageSystemType {
    COMPREHENSIVE,      // 포괄임금제
    NON_COMPREHENSIVE   // 비포괄임금제
}
