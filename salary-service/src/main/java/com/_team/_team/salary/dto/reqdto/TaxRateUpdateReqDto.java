package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.TaxType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class TaxRateUpdateReqDto {

    @NotNull(message = "세금 유형은 필수 입니다.")
    private TaxType taxType;

    /** 근로자 부담률 */
    @NotNull(message = "세율은 필수 입니다.")
    private BigDecimal rate;

    @PositiveOrZero
    private Long incomeCeiling;

    @PositiveOrZero
    private Long incomeFloor;

    @NotNull(message = "적용 연도는 필수입니다.")
    private Integer applyYear;

    /** 회사 부담률 (선택) */
    private BigDecimal employerRate;
}
