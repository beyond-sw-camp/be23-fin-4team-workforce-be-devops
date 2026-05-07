package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.enums.PayrollType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 급여대장 생성 요청 DTO
 * - 클라이언트는 직원 ID와 정산 연월일만 전달
 * - 급여(Salary) 조회, 항목(PayrollItem) 생성, 금액 계산은 서비스에서 자동 처리
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollCreateReqDto {

    /** 대상 직원 ID */
    @NotNull(message = "직원 ID는 필수입니다.")
    private UUID memberId;

    /** 정산 연월일 (예: 2026-03-25) */
    @NotNull(message = "정산 연월일은 필수 입니다.")
    private LocalDate payrollYearMonthDay;

    /** 급여 구분 미지정 시 정기급여 */
    private PayrollType payrollType;

    /**
     * DTO -> Payroll 엔티티 변환
     * - 금액은 0으로 초기화 (PayrollItem 생성 후 recalculate 로 자동 계산)
     */
    public Payroll toEntity(UUID companyId, UUID salaryId, String targetYearMonth) {
        return Payroll.builder()
                .companyId(companyId)
                .salaryId(salaryId)
                .memberId(this.memberId)
                .payrollYearMonthDay(this.payrollYearMonthDay)
                .targetYearMonth(targetYearMonth)
                .payrollType(this.payrollType != null ? this.payrollType : PayrollType.REGULAR_MONTHLY)
                .totalPayment(0L)
                .totalDeduction(0L)
                .netPay(0L)
                .build();
    }
}
