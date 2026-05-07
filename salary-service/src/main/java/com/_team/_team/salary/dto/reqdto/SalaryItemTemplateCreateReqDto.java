package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.TaxCategory;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * 급여 항목 템플릿 생성 요청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryItemTemplateCreateReqDto {

    @NotNull(message = "항목명은 필수입니다.")
    private String itemName;

    @NotNull(message = "항목 유형은 필수입니다.")
    private ItemType itemType;

    @NotNull(message = "표시 순서는 필수입니다.")
    private Integer displayOrder;

    @NotNull(message = "과세 여부는 필수입니다.")
    private String isTaxableYn;

    // 통상임금 포함 여부 Y면 시급 환산 기준에 포함
    // 정기 일률 고정 지급 항목만 Y 변동성 수당 및 비과세 실비 N
    private String isOrdinaryWageYn;

    // 회사 기본 지급 금액
    private Long defaultAmount;

    // 회사 공통 적용 여부 Y/N
    private String applyToAllYn;

    public SalaryItemTemplate toEntity(UUID companyId) {
        return SalaryItemTemplate.builder()
                .companyId(companyId)
                .itemName(itemName)
                .itemType(itemType)
                .displayOrder(displayOrder)
                .isTaxableYn(isTaxableYn)
                .isOrdinaryWageYn("Y".equalsIgnoreCase(isOrdinaryWageYn) ? "Y" : "N")
                .defaultAmount(defaultAmount)
                .applyToAllYn("Y".equalsIgnoreCase(applyToAllYn) ? "Y" : "N")
                // 커스텀 등록 시 자동 매핑
                .taxCategory("Y".equals(isTaxableYn)
                        ? TaxCategory.TAXABLE
                        : TaxCategory.ETC_NON_TAXABLE)
                .build();
    }
}
