package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.enums.TaxReductionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryResDto {

    private UUID salaryId;
    private UUID memberId;
    private UUID companyId;
    private UUID salaryPolicyId;
    private Long baseSalary;
    private Integer step;
    private String jobGradeName;
    private String jobTitleName;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Integer dependentCount;
    private Integer childUnder20Count;
    private TaxReductionType taxReductionType;
    private BigDecimal taxReductionRate;
    private LocalDate taxReductionEffectiveTo;

    // member-service Feign 결합
    private String sabun;
    private String name;
    private String organizationName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalaryResDto fromEntity(Salary salary){
        return SalaryResDto.builder()
                .salaryId(salary.getSalaryId())
                .memberId(salary.getMemberId())
                .companyId(salary.getCompanyId())
                .salaryPolicyId(salary.getSalaryPolicyId())
                .baseSalary(salary.getBaseSalary())
                .step(salary.getStep())
                .jobGradeName(salary.getJobGradeName())
                .jobTitleName(salary.getJobTitleName())
                .effectiveFrom(salary.getEffectiveFrom())
                .effectiveTo(salary.getEffectiveTo())
                .dependentCount(salary.getDependentCount())
                .childUnder20Count(salary.getChildUnder20Count())
                .taxReductionType(salary.getTaxReductionType())
                .taxReductionRate(salary.getTaxReductionRate())
                .taxReductionEffectiveTo(salary.getTaxReductionEffectiveTo())
                .createdAt(salary.getCreatedAt())
                .updatedAt(salary.getUpdatedAt())
                .build();
    }

    // 직원 정보 결합 버전 회사 단위 목록 화면용
    public static SalaryResDto fromEntity(Salary salary,
                                          String sabun,
                                          String name,
                                          String organizationName) {
        SalaryResDto base = fromEntity(salary);
        base.setSabun(sabun);
        base.setName(name);
        base.setOrganizationName(organizationName);
        return base;
    }
}
