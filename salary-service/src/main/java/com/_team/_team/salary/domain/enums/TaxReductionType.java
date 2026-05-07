package com._team._team.salary.domain.enums;

/**
 * 소득세 감면 유형 (조세특례제한법 등)
 * - NONE: 감면 없음 (기본)
 * - YOUTH_SME: 청년 중소기업 취업자 소득세 감면 (최대 5년, 90% 또는 70%)
 * - DISABLED: 장애인 감면
 * - FOREIGNER: 외국인 단일세율 (별도 처리)
 * - ETC: 기타 (taxReductionRate 로 직접 비율 지정)
 *
 * 실제 감면율(0.00 ~ 1.00) 은 Salary.taxReductionRate 에서 별도로 받음
 * (예: YOUTH_SME 90% 감면 -> type=YOUTH_SME, rate=0.90)
 */
public enum TaxReductionType {
    NONE,
    YOUTH_SME,
    DISABLED,
    FOREIGNER,
    ETC
}
