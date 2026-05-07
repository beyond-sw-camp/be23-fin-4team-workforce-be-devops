package com._team._team.contract.dto.resdto;

import com._team._team.contract.domain.ContractBatch;
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
public class ContractBatchResDto {
    private UUID batchId;
    private UUID companyId;
    private UUID templateId;
    private String templateName;
    private String batchName;
    private ContractType contractType;
    private Integer totalCount;
    private Integer signedCount;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private UUID previousBatchId;
    private Integer rejectedCount;

    public static ContractBatchResDto fromEntity(ContractBatch entity) {
        return ContractBatchResDto.builder()
                .batchId(entity.getBatchId())
                .companyId(entity.getCompanyId())
                .templateId(entity.getContractTemplate().getTemplateId())
                .templateName(entity.getContractTemplate().getTemplateName())
                .batchName(entity.getBatchName())
                .contractType(entity.getContractTemplate().getContractType())
                .totalCount(entity.getTotalCount())
                .signedCount(entity.getSignedCount())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .previousBatchId(entity.getPreviousBatchId())
                .rejectedCount(entity.getRejectedCount())
                .build();
    }
}
