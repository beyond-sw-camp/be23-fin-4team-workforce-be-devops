package com._team._team.salary.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 월별 4대보험 + 원천세 집계 단일 응답
// 회사 부담은 TaxRate 의 employerRate 비율 추정값 (참고용)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class TaxSummaryResDto {

    // 조회 연월 표시용
    private String yearMonth;

    // 대상 직원 수
    private long memberCount;

    // 4대보험 직원 부담 합계 원
    private long nationalPension;     // 국민연금
    private long healthInsurance;     // 건강보험
    private long longTermCare;        // 장기요양보험
    private long employmentInsurance; // 고용보험

    // 4대보험 회사 부담 추정값 원 정확한 신고는 EDI 기준
    private long nationalPensionEmployer;
    private long healthInsuranceEmployer;
    private long longTermCareEmployer;
    private long employmentInsuranceEmployer;
    private long industrialAccidentEmployer; // 산재보험 회사 100 부담

    // 원천세 합계 원 회사가 국세청에 신고할 금액
    private long incomeTax;          // 소득세
    private long localIncomeTax;     // 지방소득세

    // 합계
    private long fourInsuranceTotal;          // 4대보험 직원 합계
    private long fourInsuranceEmployerTotal;  // 4대보험 회사 합계 (산재 포함)
    private long withholdingTotal;            // 원천세 합계
}
