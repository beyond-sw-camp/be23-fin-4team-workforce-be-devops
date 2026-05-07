package com._team._team.salary.domain.enums;

public enum TaxType {
    NATIONAL_PENSION(true),       // 국민연금, 기준소득월액 상한 적용
    HEALTH_INSURANCE(true),       // 건강보험, 보수월액 상한 적용
    LONG_TERM_CARE(false),        // 장기요양, 건강보험료 기반이라 별도 상한 없음
    EMPLOYMENT_INSURANCE(false),  // 고용보험
    ACCIDENT_INSURANCE(false),    // 산재보험
    INCOME_TAX(false),            // 소득세
    LOCAL_INCOME_TAX(false);      // 지방소득세

    private final boolean supportsIncomeCap;

    TaxType(boolean supportsIncomeCap) {
        this.supportsIncomeCap = supportsIncomeCap;
    }

    public boolean supportsIncomeCap() {
        return supportsIncomeCap;
    }
}