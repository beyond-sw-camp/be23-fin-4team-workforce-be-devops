package com._team._team.personnel.dto;

import com._team._team.personnel.domain.PersonnelOrder;
import com._team._team.personnel.domain.enums.PersonnelOrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PersonnelOrderResDto {
    private UUID personnelOrderId;
    private UUID memberId;
    private UUID companyId;
    private PersonnelOrderType orderType;
    private LocalDate effectiveDate;
    private UUID approvalDocumentId;
    private String beforeOrganizationName;
    private String afterOrganizationName;
    private String beforeJobGradeName;
    private String afterJobGradeName;
    private String beforeJobTitleName;
    private String afterJobTitleName;
    private String reason;
    private LocalDateTime createdAt;

    public static PersonnelOrderResDto fromEntity(PersonnelOrder o) {
        return PersonnelOrderResDto.builder()
                .personnelOrderId(o.getPersonnelOrderId())
                .memberId(o.getMemberId())
                .companyId(o.getCompanyId())
                .orderType(o.getOrderType())
                .effectiveDate(o.getEffectiveDate())
                .approvalDocumentId(o.getApprovalDocumentId())
                .beforeOrganizationName(o.getBeforeOrganizationName())
                .afterOrganizationName(o.getAfterOrganizationName())
                .beforeJobGradeName(o.getBeforeJobGradeName())
                .afterJobGradeName(o.getAfterJobGradeName())
                .beforeJobTitleName(o.getBeforeJobTitleName())
                .afterJobTitleName(o.getAfterJobTitleName())
                .reason(o.getReason())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
