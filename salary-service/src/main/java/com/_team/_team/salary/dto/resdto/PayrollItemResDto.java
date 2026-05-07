package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.enums.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 급여대장 항목 응답 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollItemResDto {

    private UUID payrollItemId;
    private UUID payrollId;
    private String itemName;
    private ItemType itemType;
    private Long amount;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PayrollItemResDto fromEntity(PayrollItem item){
        return PayrollItemResDto.builder()
                .payrollItemId(item.getPayrollItemId())
                .payrollId(item.getPayroll().getPayrollId())
                .itemName(item.getItemName())
                .itemType(item.getItemType())
                .amount(item.getAmount())
                .displayOrder(item.getDisplayOrder())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
