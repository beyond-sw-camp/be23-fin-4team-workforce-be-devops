package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.CompanyLeaveType;
import com._team._team.attendance.domain.enums.BalanceType;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CompanyLeaveTypeResDto {

    private UUID companyLeaveTypeId;
    private String code;
    private String name;
    private BalanceType balanceType;
    private Double daysPerUse;
    private Boolean isSystemDefault;
    private String isPaidYn;
    private Double maxDaysPerYear;
    private String requireEvidenceYn;
    private Integer displayOrder;

    public static CompanyLeaveTypeResDto fromEntity(CompanyLeaveType e) {
        return CompanyLeaveTypeResDto.builder()
                .companyLeaveTypeId(e.getCompanyLeaveTypeId())
                .code(e.getCode())
                .name(e.getName())
                .balanceType(e.getBalanceType())
                .daysPerUse(e.getDaysPerUse())
                .isSystemDefault(e.getIsSystemDefault())
                .isPaidYn(e.getIsPaidYn())
                .maxDaysPerYear(e.getMaxDaysPerYear())
                .requireEvidenceYn(e.getRequireEvidenceYn())
                .displayOrder(e.getDisplayOrder())
                .build();
    }
}
