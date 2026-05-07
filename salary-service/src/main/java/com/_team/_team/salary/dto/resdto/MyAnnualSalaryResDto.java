package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.enums.PayrollStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 직원 본인 연봉 조회 화면용 응답
// 연도 합계 KPI 카드 + 월별 표 + 항목별 합계 표
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MyAnnualSalaryResDto {

    private int year;

    // 월별 행 1월 ~ 12월 (없는 달도 채워서 12개 고정)
    private List<MonthlyRow> monthly;

    // 항목별 누적 (지급 항목만 / 공제 항목만)
    private List<ItemBreakdown> earnings;
    private List<ItemBreakdown> deductions;

    // KPI
    private long totalPayment;     // 연 총지급
    private long totalDeduction;   // 연 총공제
    private long netPay;           // 연 실수령
    private long monthlyAverage;   // 월평균 실수령 (정산 건수 기준)
    private int  payrollCount;     // 연 정산 건수

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class MonthlyRow {
        private int month;                 // 1 ~ 12 (정산 대상 월 기준)
        private String payrollId;          // 없으면 null
        private String payrollYearMonthDay; // 지급일
        private String targetYearMonth;    // 정산 대상 월
        private PayrollStatus payrollStatus;
        private long totalPayment;
        private long totalDeduction;
        private long netPay;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class ItemBreakdown {
        private String itemName;
        private long totalAmount;
    }
}
