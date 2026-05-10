package com._team._team.salary.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MySalaryHistoryResDto {
    /** 급여 행 ID */
    private UUID salaryId;

    /** 변경 전 기본급 (직전 행, 첫 행이면 null) */
    private Long previousBaseSalary;

    /** 변경 후 기본급 (이번 행) */
    private Long currentBaseSalary;

    /** 변동률 % (첫 행이면 null) */
    private Double changeRate;

    /** 직급명 (당시 스냅샷) */
    private String jobGradeName;

    /** 직책명 (당시 스냅샷) */
    private String jobTitleName;

    /** 적용 시작일 */
    private LocalDate effectiveFrom;

    /** 적용 종료일 (현재 적용 중이면 null) */
    private LocalDate effectiveTo;
}
