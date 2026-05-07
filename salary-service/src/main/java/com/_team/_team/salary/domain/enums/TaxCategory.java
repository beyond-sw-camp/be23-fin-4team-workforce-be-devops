package com._team._team.salary.domain.enums;

/**
 * 세법 카테고리, 비과세 한도 판정 기준
 * 비과세 여부 + 월 한도 판정, 국세청 소득세법 시행령 기반
 */
public enum TaxCategory {
    TAXABLE,            // 일반 과세
    MEAL,               // 식대, 월 20만원 비과세
    VEHICLE_SELF,       // 자가운전보조금, 월 20만원 비과세
    CHILDCARE,          // 출산 및 보육수당, 월 20만원 비과세
    TUITION,            // 학자금, 업무 관련 실비
    RESEARCH,           // 연구활동비, 월 20만원
    HAZARD_REMOTE,      // 벽지수당, 월 20만원
    OVERSEAS_WORK,      // 국외근로, 월 100만원
    ETC_NON_TAXABLE;     // 기타 비과세 (시스템 관리자 승인 후 사용)

    public Long getMonthlyNonTaxableLimit() {
        return switch (this) {
            case MEAL, VEHICLE_SELF, CHILDCARE, RESEARCH, HAZARD_REMOTE -> 200_000L;
            case OVERSEAS_WORK -> 1_000_000L;
            case TAXABLE -> 0L;
            case TUITION, ETC_NON_TAXABLE -> null;
        };
    }
}