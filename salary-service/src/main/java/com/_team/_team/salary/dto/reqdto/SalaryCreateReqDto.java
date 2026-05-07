package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.Salary;
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

/**
 * 급여 생성 요청 DTO
 * - 직원에게 급여 정책을 연결하고 기본급을 설정
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryCreateReqDto {

    @NotNull(message = "직원 ID는 필수입니다.")
    private UUID memberId;

    @NotNull(message = "급여 정책 ID는 필수입니다.")
    private UUID salaryPolicyId;

    private Long baseSalary;

    @Positive
    private Integer step;

    /** 직급명 (선택) */
    private String jobGradeName;

    /** 직책명 (선택) */
    private String jobTitleName;

    @NotNull(message = "적용 시작일은 필수입니다.")
    private LocalDate effectiveFrom;

    /** 적용 종료일 (null이면 현재 적용 중) */
    private LocalDate effectiveTo;

    /** 부양가족수 간이세액표 룩업용 미입력 시 1 본인 만 */
    @Min(value = 0, message = "부양가족수는 0 이상이어야 합니다.")
    @Max(value = 11, message = "부양가족수는 11 이하여야 합니다.")
    private Integer dependentCount;

    /** 8세 이상 20세 이하 자녀 수 , 자녀세액공제 차원  */
    @Min(value = 0, message = "자녀수는 0 이상이어야 합니다.")
    @Max(value = 11, message = "자녀수는 11 이하여야 합니다.")
    private Integer childUnder20Count;

    /** 소득세 감면 유형 */
    private TaxReductionType taxReductionType;

    /** 감면율 0.00 ~ 1.00 (예: 청년 SME 90% -> 0.90) */
    @DecimalMin(value = "0.00", message = "감면율은 0 이상입니다.")
    @DecimalMax(value = "1.00", message = "감면율은 1 이하입니다.")
    private BigDecimal taxReductionRate;

    /** 감면 종료일 - 청년 SME 5년 한정 등 */
    private LocalDate taxReductionEffectiveTo;

    /**
     * DTO -> Entity 변환
     * - companyId는 Gateway 헤더에서 전달받으므로 파라미터로 받음
     */
    public Salary toEntity(UUID companyId){
        return Salary.builder()
                .memberId(memberId)
                .companyId(companyId)
                .salaryPolicyId(salaryPolicyId)
                .baseSalary(baseSalary)
                .step(step)
                .jobGradeName(jobGradeName)
                .jobTitleName(jobTitleName)
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .dependentCount(dependentCount == null ? 1 : dependentCount)
                .childUnder20Count(childUnder20Count == null ? 0 : childUnder20Count)
                .taxReductionType(taxReductionType == null ? TaxReductionType.NONE : taxReductionType)
                .taxReductionRate(taxReductionRate)
                .taxReductionEffectiveTo(taxReductionEffectiveTo)
                .build();
    }
}
