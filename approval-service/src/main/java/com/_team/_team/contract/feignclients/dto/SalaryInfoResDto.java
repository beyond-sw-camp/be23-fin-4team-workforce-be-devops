package com._team._team.contract.feignclients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SalaryInfoResDto {
    private UUID salaryId;
    private UUID memberId;
    private Long baseSalary;
    private Integer step;
    private String jobGradeName;
    private String jobTitleName;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String sabun;
    private String name;
    private String organizationName;
}
