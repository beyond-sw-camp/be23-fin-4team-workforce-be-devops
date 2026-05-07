package com._team._team.contract.dto.resdto;

import com._team._team.contract.domain.ContractTemplate;
import com._team._team.contract.domain.enums.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ContractTemplateResDto {

    private UUID templateId;
    private UUID companyId;
    private String templateName;
    private ContractType contractType;
    private String formSchema;
    private String isActiveYn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ContractTemplateResDto fromEntity(ContractTemplate entity) {
        return ContractTemplateResDto.builder()
                .templateId(entity.getTemplateId())
                .companyId(entity.getCompanyId())
                .templateName(entity.getTemplateName())
                .contractType(entity.getContractType())
                .formSchema(entity.getFormSchema())
                .isActiveYn(entity.getIsActiveYn())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
