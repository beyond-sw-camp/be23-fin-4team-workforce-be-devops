package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.domain.enums.PayrollType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollResDto {

    private UUID payrollId;
    private UUID salaryId;
    private UUID memberId;
    private LocalDate payrollYearMonthDay;
    /** 정산 대상 월 , 어느 월분 급여인지 */
    private String targetYearMonth;
    private LocalDate paidAt;
    private Long totalPayment;
    private Long totalDeduction;
    private Long netPay;
    private PayrollStatus payrollStatus;
    private PayrollType payrollType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PayrollResDto fromEntity(Payroll payroll) {
        return PayrollResDto.builder()
                .payrollId(payroll.getPayrollId())
                .salaryId(payroll.getSalaryId())
                .memberId(payroll.getMemberId())
                .payrollYearMonthDay(payroll.getPayrollYearMonthDay())
                .targetYearMonth(payroll.getTargetYearMonth())
                .paidAt(payroll.getPaidAt())
                .totalPayment(payroll.getTotalPayment())
                .totalDeduction(payroll.getTotalDeduction())
                .netPay(payroll.getNetPay())
                .payrollStatus(payroll.getPayrollStatus())
                .payrollType(payroll.getPayrollType())
                .createdAt(payroll.getCreatedAt())
                .updatedAt(payroll.getUpdatedAt())
                .build();
    }
}
