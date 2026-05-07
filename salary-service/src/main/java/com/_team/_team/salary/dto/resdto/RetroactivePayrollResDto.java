package com._team._team.salary.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * 소급분 재계산 결과 미리보기 + 발행 응답
 *  preview 시 totalDiff > 0 이면 발행 가능 표시
 *  apply 시 newPayrollId 채워짐
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RetroactivePayrollResDto {

    private UUID memberId;
    private YearMonth fromMonth;
    private YearMonth toMonth;

    // 기존 통상임금 (소급 발행 시점 기존 가산수당이 산정된 값 평균)
    private long previousOrdinaryWage;
    // 새 통상임금
    private long newOrdinaryWage;

    // 월별 차액 breakdown
    private List<MonthlyDiff> monthlyDiffs;

    // 차액 총합 (음수면 0 으로 처리 환수 시나리오는 별도)
    private long totalDiff;

    // apply 결과 — 발행된 RETROACTIVE Payroll ID
    private UUID newPayrollId;
    // apply 시 발행일
    private String issuedDate;

    // 사용자 안내
    private String message;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class MonthlyDiff {
        private YearMonth month;
        // 기존 가산수당 합계 (연장 + 야간 + 휴일)
        private long oldAllowance;
        // 새 가산수당 합계 (재계산)
        private long newAllowance;
        // 차액 (new - old, 음수 가능)
        private long diff;
        // 원본 Payroll ID (참조용)
        private UUID sourcePayrollId;
    }
}
