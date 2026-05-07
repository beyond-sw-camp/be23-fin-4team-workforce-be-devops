package com._team._team.contract.dto.resdto;


import com._team._team.contract.domain.Contract;
import com._team._team.contract.domain.ContractParty;
import com._team._team.contract.domain.enums.ContractStatus;
import com._team._team.contract.domain.enums.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ContractResDto {

    private UUID contractId;
    private UUID companyId;
    private UUID templateId;
    private String templateName;
    private UUID batchId;
    private UUID employeeMemberId;
    private ContractType contractType;
    private String contentJson;
    private String formSchemaSnapshot;
    private ContractStatus contractStatus;
    private String signedPdfUrl;
    private String sealImageUrl;
    private String employeeName;
    private String employeeSabun;
    private String organizationName;
    private String jobTitleName;
    private List<ContractPartyResDto> parties;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String rejectReason;
    private UUID previousContractId;
    private Integer revision;
    private String cancelReason;
    private String contractNumber;

    public static ContractResDto fromEntity(Contract entity, List<ContractParty> parties) {
        return ContractResDto.builder()
                .contractId(entity.getContractId())
                .companyId(entity.getCompanyId())
                .templateId(entity.getContractTemplate().getTemplateId())
                .templateName(entity.getContractTemplate().getTemplateName())
                .batchId(entity.getContractBatch() != null ? entity.getContractBatch().getBatchId() : null)
                .employeeMemberId(entity.getEmployeeMemberId())
                .contractType(entity.getContractType())
                .contentJson(entity.getContentJson())
                .formSchemaSnapshot(entity.getFormSchemaSnapshot())
                .contractStatus(entity.getContractStatus())
                .signedPdfUrl(entity.getSignedPdfUrl())
                .sealImageUrl(entity.getSealImageUrl())
                .employeeName(entity.getEmployeeName())
                .employeeSabun(entity.getEmployeeSabun())
                .organizationName(entity.getOrganizationName())
                .jobTitleName(entity.getJobTitleName())
                .parties(parties.stream()
                        .map(ContractPartyResDto::fromEntity)
                        .toList())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .rejectReason(entity.getRejectReason())
                .previousContractId(entity.getPreviousContractId())
                .revision(entity.getRevision())
                .cancelReason(entity.getCancelReason())
                .contractNumber(entity.getContractNumber())
                .build();
    }

}
