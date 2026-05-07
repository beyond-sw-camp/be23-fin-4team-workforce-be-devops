package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.TaxRate;
import com._team._team.salary.domain.enums.TaxType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 세율 응답 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class TaxRateResDto {
    private UUID taxRateId;
    private TaxType taxType;
    private BigDecimal rate;
    private Long incomeCeiling;
    private Long incomeFloor;
    private Integer applyYear;
    private BigDecimal employerRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaxRateResDto fromEntity(TaxRate taxRate){
        return TaxRateResDto.builder()
                .taxRateId(taxRate.getTaxRateId())
                .taxType(taxRate.getTaxType())
                .rate(taxRate.getRate())
                .incomeCeiling(taxRate.getIncomeCeiling())
                .incomeFloor(taxRate.getIncomeFloor())
                .applyYear(taxRate.getApplyYear())
                .employerRate(taxRate.getEmployerRate())
                .createdAt(taxRate.getCreatedAt())
                .updatedAt(taxRate.getUpdatedAt())
                .build();
    }
}
