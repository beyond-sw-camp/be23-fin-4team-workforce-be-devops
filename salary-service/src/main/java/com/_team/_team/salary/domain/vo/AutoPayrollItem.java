package com._team._team.salary.domain.vo;
import com._team._team.salary.domain.enums.ItemType;

/**
 * 자동 계산된 급여 항목 전달용 값 객체
 *
 * PayrollCalculationService -> PayrollService로 계산 결과 전달 시 사용
 * PayrollItem 엔티티를 직접 생성하지 않고 값 객체로 분리
 *   엔티티 생성 책임은 PayrollService에 위임
 *   초과근무수당, 공휴일근무수당, 4대보험 공제 등에 사용
 *  예시:
 *   new AutoPayrollItem("초과근무수당", ItemType.EARNING, 48444L, 90)
 *   new AutoPayrollItem("국민연금", ItemType.DEDUCTION, 172743L, 200)
 */
public record AutoPayrollItem(
        String itemName,       // 항목명 (예: "초과근무수당", "국민연금")
        ItemType itemType,     // EARNING(지급) / DEDUCTION(공제)
        long amount,           // 금액 (원 단위, floor 처리)
        int displayOrder,       // 표시 순서
        String isTaxableYn     // Y: 과세 / N: 비과세
) {
}
