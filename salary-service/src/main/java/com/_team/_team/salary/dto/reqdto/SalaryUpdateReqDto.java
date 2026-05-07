package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.TaxReductionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryUpdateReqDto {

    @NotNull(message = "급여 정책 ID는 필수 입니다.")
    private UUID salaryPolicyId;

    @NotNull(message = "기본급은 필수 입니다.")
    private Long baseSalary;

    @Positive
    private Integer step;

    private String jobGradeName;

    private String jobTitleName;

    @NotNull(message = "적용 시작일은 필수입니다.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    /** 부양가족수 간이세액표 룩업용 미입력 시 1 본인 만 */
    @Min(value = 0, message = "부양가족수는 0 이상이어야 합니다.")
    @Max(value = 11, message = "부양가족수는 11 이하여야 합니다.")
    private Integer dependentCount;

    /** 8세 이상 20세 이하 자녀 수 */
    @Min(value = 0)
    @Max(value = 11)
    private Integer childUnder20Count;

    /** 소득세 감면 유형 */
    private TaxReductionType taxReductionType;

    /** 감면율 0.00 ~ 1.00 */
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "1.00")
    private BigDecimal taxReductionRate;

    /** 감면 종료일 */
    private LocalDate taxReductionEffectiveTo;
}
