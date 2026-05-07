package com._team._team.salary.dto.resdto;


import com._team._team.salary.domain.RetirementPolicy;
import com._team._team.salary.domain.enums.RetirementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class RetirementPolicyResDto {
    private UUID retirementPolicyId;
    private UUID companyId;
    private RetirementType retirementType;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String memo;
    // DC 월 부담금 비율(%)
    private BigDecimal dcContributionRate;
    // 운용 금융기관명
    private String providerName;
    // 운용 계약/계좌번호
    private String contractNumber;
    // LEGAL 중간정산 허용 Y/N
    private String allowEarlySettlementYn;
    // 오늘 기준 활성 여부
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RetirementPolicyResDto fromEntity(RetirementPolicy p) {
        return RetirementPolicyResDto.builder()
                .retirementPolicyId(p.getRetirementPolicyId())
                .companyId(p.getCompanyId())
                .retirementType(p.getRetirementType())
                .effectiveFrom(p.getEffectiveFrom())
                .effectiveTo(p.getEffectiveTo())
                .memo(p.getMemo())
                .dcContributionRate(p.getDcContributionRate())
                .providerName(p.getProviderName())
                .contractNumber(p.getContractNumber())
                .allowEarlySettlementYn(p.getAllowEarlySettlementYn())
                .active(p.isActiveAt(LocalDate.now()))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}