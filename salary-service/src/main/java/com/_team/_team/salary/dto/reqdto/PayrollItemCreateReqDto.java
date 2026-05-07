package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.SalaryItemTemplate;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


/**
 * 급여대장 항목 생성 요청 DTO
 * - 프론트에서 템플릿 ID와 금액만 전달
 * - 항목명(itemName), 유형(itemType)은 Service에서 템플릿 조회 후 자동 세팅
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollItemCreateReqDto {

    /** 급여 항목 템플릿 ID */
    @NotNull(message = "급여 항목 템플릿은 필수입니다.")
    private UUID salaryItemTemplateId;

    /** 금액 */
    @NotNull(message = "금액은 필수입니다.")
    private Long amount;

    public PayrollItem toEntity(Payroll payroll, SalaryItemTemplate template) {
        return PayrollItem.builder()
                .payroll(payroll)
                .itemName(template.getItemName())
                .itemType(template.getItemType())
                .amount(this.amount)
                .displayOrder(template.getDisplayOrder())
                .isTaxableYn(template.getIsTaxableYn())
                .build();
    }
}
