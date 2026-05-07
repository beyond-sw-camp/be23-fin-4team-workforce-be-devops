package com._team._team.salary.domain.enums;

// 퇴직급여 제도 유형
public enum RetirementType {
    LEGAL, // 법정 퇴직금 회사 사내 적립 일시금 지급
    DB,    // 확정급여형 외부 금융기관 적립 계산식 LEGAL 동일
    DC      // 확정기여형 외부 금융기관 매월 1 12 적립 운용수익 별도
}