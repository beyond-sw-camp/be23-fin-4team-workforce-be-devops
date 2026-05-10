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

// 보너스 정책 수정 effectiveFrom 은 변경 불가
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class BonusPolicyUpdateReqDto {

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

    // 평가 등급 - 성과급 비율
    private String gradeBonusRatesJson;

    /* 명절상여 */
    private String useHolidayBonusYn;

    private HolidayBonusType holidayBonusType;

    @DecimalMin(value = "0.0", message = "명절상여 값은 0 이상이어야 합니다.")
    private BigDecimal holidayBonusValue;

    /* 공통 */
    @NotNull(message = "지급 대상 범위는 필수입니다.")
    private BonusEligibilityScope eligibilityScope;

    @Min(value = 0, message = "최소 근속 월수는 0 이상이어야 합니다.")
    private Integer minTenureMonths;

    private String excludeOnLeaveYn;

    private LocalDate effectiveTo;

    private String memo;
}
