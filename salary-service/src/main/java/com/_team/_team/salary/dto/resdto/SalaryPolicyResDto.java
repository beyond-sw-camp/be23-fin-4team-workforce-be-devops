package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.PayCycleType;
import com._team._team.salary.domain.enums.PayDayShiftRule;
import com._team._team.salary.domain.enums.ProrationMethod;
import com._team._team.salary.domain.enums.WageSystemType;
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
public class SalaryPolicyResDto {
    private UUID salaryPolicyId;
    private UUID companyId;
    private String policyName;
    private Integer payDay;
    private PayDayShiftRule payDayShiftRule;
    private String usePayGradeYn;
    private WageSystemType wageSystemType;
    private Integer fixedOvertimeMinutes;
    // 월 소정근로시간 시급 환산 기준 한국 표준 209
    private Integer monthlyOrdinaryHours;
    // 일할계산 방식
    private ProrationMethod prorationMethod;
    /** 급여 지급 주기 (당월분/전월분) */
    private PayCycleType payCycleType;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalaryPolicyResDto fromEntity(SalaryPolicy salaryPolicy){
        return SalaryPolicyResDto.builder()
                .salaryPolicyId(salaryPolicy.getSalaryPolicyId())
                .companyId(salaryPolicy.getCompanyId())
                .policyName(salaryPolicy.getPolicyName())
                .payDay(salaryPolicy.getPayDay())
                .payDayShiftRule(salaryPolicy.getPayDayShiftRule())
                .usePayGradeYn(salaryPolicy.getUsePayGradeYn())
                .wageSystemType(salaryPolicy.getWageSystemType())
                .fixedOvertimeMinutes(salaryPolicy.getFixedOvertimeMinutes())
                .monthlyOrdinaryHours(salaryPolicy.getMonthlyOrdinaryHours())
                .prorationMethod(salaryPolicy.getProrationMethod())
                .payCycleType(salaryPolicy.getPayCycleType())
                .effectiveFrom(salaryPolicy.getEffectiveFrom())
                .effectiveTo(salaryPolicy.getEffectiveTo())
                .createdAt(salaryPolicy.getCreatedAt())
                .updatedAt(salaryPolicy.getUpdatedAt())
                .build();
    }
}
