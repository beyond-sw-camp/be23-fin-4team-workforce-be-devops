package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.TaxCategory;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 급여 항목 템플릿 응답 DTO
 */

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryItemTemplateResDto {

    private UUID salaryItemTemplateId;
    private UUID companyId;
    private String itemName;
    private ItemType itemType;
    private Integer displayOrder;
    private String isTaxableYn;

    // 통상임금 포함 여부 시급 환산 기준
    private String isOrdinaryWageYn;

    // 세법 카테고리 카탈로그 기반 자동 복사 또는 관리자 선택
    private TaxCategory taxCategory;

    // 월 비과세 한도 카테고리에 따라 결정 한도 없음이면 null 일반 과세는 0
    private Long monthlyNonTaxableLimit;

    // 회사 기본 지급 금액
    private Long defaultAmount;

    // 회사 공통 적용 여부 Y/N
    private String applyToAllYn;

    private String delYn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalaryItemTemplateResDto fromEntity(SalaryItemTemplate template) {
        TaxCategory cat = template.getTaxCategory();
        return SalaryItemTemplateResDto.builder()
                .salaryItemTemplateId(template.getSalaryItemTemplateId())
                .companyId(template.getCompanyId())
                .itemName(template.getItemName())
                .itemType(template.getItemType())
                .displayOrder(template.getDisplayOrder())
                .isTaxableYn(template.getIsTaxableYn())
                .isOrdinaryWageYn(template.getIsOrdinaryWageYn())
                .taxCategory(cat)
                .monthlyNonTaxableLimit(cat != null ? cat.getMonthlyNonTaxableLimit() : null)
                .defaultAmount(template.getDefaultAmount())
                .applyToAllYn(template.getApplyToAllYn())
                .delYn(template.getDelYn())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
