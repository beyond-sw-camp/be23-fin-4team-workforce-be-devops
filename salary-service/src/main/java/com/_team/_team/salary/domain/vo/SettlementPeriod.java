package com._team._team.salary.domain.vo;
import java.time.LocalDate;

/**
 * 급여 정산 집계 구간(from~to, 양끝 포함).
 * PayrollCalculationService에서 급여정책 기준으로 산출해 PayrollService 등에 넘길 때 사용
 * Java record 사용: 불변 객체, getter/equals/hashCode 자동 생성
 *
 *   FIRST + LAST, payDay=25, payrollDate=2026-03-25
 *   → SettlementPeriod(from=2026-03-01, to=2026-03-31)
 *
 *   SPECIFIC + SPECIFIC, payDay=25, payrollDate=2026-03-25
 *   → SettlementPeriod(from=2026-02-26, to=2026-03-25)
 */
public record SettlementPeriod(LocalDate from, LocalDate to) {
}
