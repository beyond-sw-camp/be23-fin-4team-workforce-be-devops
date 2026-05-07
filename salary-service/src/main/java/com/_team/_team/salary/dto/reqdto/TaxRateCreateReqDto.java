package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.TaxRate;
import com._team._team.salary.domain.enums.TaxType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

/**
 * 세율 생성 요청 DTO
 * - 연도별 세금 유형과 부담률을 등록
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class TaxRateCreateReqDto {

    @NotNull(message = "세금 유형은 필수 입니다.")
    private TaxType taxType;

    /** 근로자 부담률 (예: 0.0450) */
    @NotNull(message = "세율은 필수입니다.")
    private BigDecimal rate;

    @PositiveOrZero
    private Long incomeCeiling;

    @PositiveOrZero
    private Long incomeFloor;

    @NotNull(message = "적용 연도는 필수 입니다.")
    private Integer applyYear;

    /** 회사 부담률 (선택, 4대보험에만 해당) */
    private BigDecimal employerRate;

    public TaxRate toEntity(){
        return TaxRate.builder()
                .taxType(taxType)
                .rate(rate)
                .applyYear(applyYear)
                .employerRate(employerRate)
                .build();
    }
}
