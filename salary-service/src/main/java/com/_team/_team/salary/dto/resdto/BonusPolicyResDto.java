package com._team._team.salary.dto.resdto;


import com._team._team.salary.domain.BonusPolicy;
import com._team._team.salary.domain.enums.BonusEligibilityScope;
import com._team._team.salary.domain.enums.HolidayBonusType;
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
public class BonusPolicyResDto {
    private UUID bonusPolicyId;
    private UUID companyId;

    private String useRegularBonusYn;
    private BigDecimal regularBonusAnnualRate;
    private Integer regularBonusPaymentCount;

    private String usePerformanceBonusYn;
    private BigDecimal performanceBonusMaxRate;
    private String performanceBonusBasis;
    // 평가 등급 - 성과급 비율
    private String gradeBonusRatesJson;

    private String useHolidayBonusYn;
    private HolidayBonusType holidayBonusType;
    private BigDecimal holidayBonusValue;

    private BonusEligibilityScope eligibilityScope;
    private Integer minTenureMonths;
    private String excludeOnLeaveYn;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String memo;

    // 오늘 기준 활성 여부
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BonusPolicyResDto fromEntity(BonusPolicy p) {
        return BonusPolicyResDto.builder()
                .bonusPolicyId(p.getBonusPolicyId())
                .companyId(p.getCompanyId())
                .useRegularBonusYn(p.getUseRegularBonusYn())
                .regularBonusAnnualRate(p.getRegularBonusAnnualRate())
                .regularBonusPaymentCount(p.getRegularBonusPaymentCount())
                .usePerformanceBonusYn(p.getUsePerformanceBonusYn())
                .performanceBonusMaxRate(p.getPerformanceBonusMaxRate())
                .performanceBonusBasis(p.getPerformanceBonusBasis())
                .gradeBonusRatesJson(p.getGradeBonusRatesJson())
                .useHolidayBonusYn(p.getUseHolidayBonusYn())
                .holidayBonusType(p.getHolidayBonusType())
                .holidayBonusValue(p.getHolidayBonusValue())
                .eligibilityScope(p.getEligibilityScope())
                .minTenureMonths(p.getMinTenureMonths())
                .excludeOnLeaveYn(p.getExcludeOnLeaveYn())
                .effectiveFrom(p.getEffectiveFrom())
                .effectiveTo(p.getEffectiveTo())
                .memo(p.getMemo())
                .active(p.isActiveAt(LocalDate.now()))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
