package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.ItemType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 급여 항목 템플릿 수정 요청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryItemTemplateUpdateReqDto {

    @NotNull(message = "항목명은 필수입니다.")
    private String itemName;

    @NotNull(message = "항목 유형은 필수입니다.")
    private ItemType itemType;

    @NotNull(message = "표시 순서는 필수입니다.")
    private Integer displayOrder;

    @NotNull(message = "과세 여부는 필수입니다.")
    private String isTaxableYn;

    // 통상임금 포함 여부 null 이면 기존 값 유지 Y면 시급 환산 기준 포함
    private String isOrdinaryWageYn;

    // 회사 기본 지급 금액
    private Long defaultAmount;

    // 회사 공통 적용 여부 Y/N
    private String applyToAllYn;
}
