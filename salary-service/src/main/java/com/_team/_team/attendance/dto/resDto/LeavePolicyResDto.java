package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.domain.enums.AccrualBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeavePolicyResDto {

    private UUID policyId;
    private UUID companyId;
    private String isPromotionYn;
    private Integer promotion1stBeforeDays;
    private Integer promotion2ndBeforeDays;
    private String isCarryoverYn;
    private Integer carryoverDays;
    private String isCarryoverConsentYn;
    private String isPayoutYn;
    private Double defaultAnnualDays;
    private Double extraDaysPerInterval;
    private Integer extraIntervalYears;
    private Double maxAnnualDays;
    private AccrualBase accrualBase;

    public static LeavePolicyResDto fromEntity(LeavePolicy leavePolicy){
        return LeavePolicyResDto.builder()
                .policyId(leavePolicy.getPolicyId())
                .companyId(leavePolicy.getCompanyId())
                .isPromotionYn(leavePolicy.getIsPromotionYn())
                .promotion1stBeforeDays(leavePolicy.getPromotion1stBeforeDays())
                .promotion2ndBeforeDays(leavePolicy.getPromotion2ndBeforeDays())
                .isCarryoverYn(leavePolicy.getIsCarryoverYn())
                .carryoverDays(leavePolicy.getCarryoverDays())
                .isCarryoverConsentYn(leavePolicy.getIsCarryoverConsentYn())
                .isPayoutYn(leavePolicy.getIsPayoutYn())
                .defaultAnnualDays(leavePolicy.getDefaultAnnualDays())
                .extraDaysPerInterval(leavePolicy.getExtraDaysPerInterval())
                .extraIntervalYears(leavePolicy.getExtraIntervalYears())
                .maxAnnualDays(leavePolicy.getMaxAnnualDays())
                .accrualBase(leavePolicy.getAccrualBase())
                .build();
    }
}
