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

// 회사 월 단위 급여대장 목록 화면용
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollAdminListResDto {

    private UUID payrollId;
    private UUID memberId;

    // member-service Feign 결합
    private String sabun;
    private String name;
    private String organizationName;

    private LocalDate payrollYearMonthDay;
    /** 정산 대상 월 (YYYY-MM) */
    private String targetYearMonth;
    private LocalDate paidAt;

    private PayrollStatus payrollStatus;
    private PayrollType payrollType;

    private Long totalPayment;
    private Long totalDeduction;
    private Long netPay;

    private LocalDateTime createdAt;

    public static PayrollAdminListResDto fromEntity(Payroll p,
                                                    String sabun,
                                                    String name,
                                                    String organizationName) {
        return PayrollAdminListResDto.builder()
                .payrollId(p.getPayrollId())
                .memberId(p.getMemberId())
                .sabun(sabun)
                .name(name)
                .organizationName(organizationName)
                .payrollYearMonthDay(p.getPayrollYearMonthDay())
                .targetYearMonth(p.getTargetYearMonth())
                .paidAt(p.getPaidAt())
                .payrollStatus(p.getPayrollStatus())
                .payrollType(p.getPayrollType())
                .totalPayment(p.getTotalPayment())
                .totalDeduction(p.getTotalDeduction())
                .netPay(p.getNetPay())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
