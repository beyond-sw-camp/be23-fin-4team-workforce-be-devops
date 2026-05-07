package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.BonusEligibilityScope;
import com._team._team.salary.domain.enums.HolidayBonusType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class BonusPolicyCreateReqDto {

    /* 정기상여 */
    private String useRegularBonusYn;

    @DecimalMin(value = "0.0", message = "연 누계 비율은 0 이상이어야 합니다.")
    @DecimalMax(value = "2000.0", message = "연 누계 비율은 2000 이하여야 합니다.")
    private BigDecimal regularBonusAnnualRate;

    @Min(value = 1, message = "연 지급 횟수는 1 이상이어야 합니다.")
    @Max(value = 24, message = "연 지급 횟수는 24 이하여야 합니다.")
    private Integer regularBonusPaymentCount;

    /* 성과급 */
    private String usePerformanceBonusYn;

    @DecimalMin(value = "0.0", message = "최대 지급 비율은 0 이상이어야 합니다.")
    @DecimalMax(value = "1000.0", message = "최대 지급 비율은 1000 이하여야 합니다.")
    private BigDecimal performanceBonusMaxRate;

    private String performanceBonusBasis;

    /* 명절상여 */
    private String useHolidayBonusYn;

    private HolidayBonusType holidayBonusType;

    @DecimalMin(value = "0.0", message = "명절상여 값은 0 이상이어야 합니다.")
    private BigDecimal holidayBonusValue;

    /* 공통 */
    @NotNull(message = "지급 대상 범위는 필수입니다.")
    private BonusEligibilityScope eligibilityScope;

    /** 최소 근속 월수 - null/0 이면 근속 무관 */
    @Min(value = 0, message = "최소 근속 월수는 0 이상이어야 합니다.")
    @Max(value = 240, message = "최소 근속 월수는 240 이하여야 합니다.")
    private Integer minTenureMonths;

    /** 휴직자 제외 여부 'Y' / 'N'. 기본 'Y' */
    private String excludeOnLeaveYn;

    @NotNull(message = "적용 시작일은 필수입니다.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String memo;
}
