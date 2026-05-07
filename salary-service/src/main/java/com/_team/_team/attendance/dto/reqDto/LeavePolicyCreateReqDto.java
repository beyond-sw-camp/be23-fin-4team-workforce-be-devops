package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.domain.enums.AccrualBase;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeavePolicyCreateReqDto {

    /** 촉진제도 사용 여부 */
    private String isPromotionYn;

    /** 1차 촉진 알림 기한(일) */
    private Integer promotion1stBeforeDays;

    /** 2차 촉진 알림 기한(일) */
    private Integer promotion2ndBeforeDays;

    /** 이월 허용 여부 */
    private String isCarryoverYn;

    /** 최대 이월 일수 */
    private Integer carryoverDays;

    /** 이월 동의서 사용 여부 - 'Y' 면 직원별 동의 받아야 이월 처리 */
    private String isCarryoverConsentYn;

    /** 미사용 수당 지급 여부 */
    private String isPayoutYn;

    /** 기본 부여 일수 (회사 커스텀, null이면 15.0) 근로기준법 최소 15일 */
    @DecimalMin(value = "15.0", message = "근로기준법상 1년차 연차는 최소 15일 이상이어야 합니다.")
    private Double defaultAnnualDays;

    /** 근속별 추가 부여 단위 null 이면 1.0 근로기준법 최소 1일 */
    @DecimalMin(value = "1.0", message = "근로기준법상 추가 일수는 최소 1일 이상이어야 합니다.")
    private Double extraDaysPerInterval;

    /** 근속별 추가 부여 주기 1~2 년 (근로기준법 최대 2년) */
    @Min(value = 1, message = "주기는 1년 이상이어야 합니다.")
    @Max(value = 2, message = "근로기준법상 추가 부여 주기는 최대 2년입니다.")
    private Integer extraIntervalYears;

    /** 연차 상한 null 이면 25.0 근로기준법 최소 25일 */
    @DecimalMin(value = "25.0", message = "근로기준법상 연차 상한은 최소 25일 이상이어야 합니다.")
    private Double maxAnnualDays;

    @NotNull(message = "연차 발생 기준은 필수 입니다.")
    private AccrualBase accrualBase;

    public LeavePolicy toEntity(UUID companyId) {
        return LeavePolicy.builder()
                .companyId(companyId)
                .isPromotionYn(this.isPromotionYn != null ? this.isPromotionYn : "N")
                .promotion1stBeforeDays(this.promotion1stBeforeDays)
                .promotion2ndBeforeDays(this.promotion2ndBeforeDays)
                .isCarryoverYn(this.isCarryoverYn != null ? this.isCarryoverYn : "N")
                .carryoverDays(this.carryoverDays)
                .isCarryoverConsentYn(this.isCarryoverConsentYn != null ? this.isCarryoverConsentYn : "N")
                .isPayoutYn(this.isPayoutYn != null ? this.isPayoutYn : "N")
                .defaultAnnualDays(this.defaultAnnualDays != null ? this.defaultAnnualDays : 15.0)
                .extraDaysPerInterval(this.extraDaysPerInterval != null ? this.extraDaysPerInterval : 1.0)
                .extraIntervalYears(
                        (this.extraIntervalYears != null && this.extraIntervalYears > 0)
                                ? this.extraIntervalYears : 2)
                .maxAnnualDays(this.maxAnnualDays != null ? this.maxAnnualDays : 25.0)
                .accrualBase(this.accrualBase)
                .build();
    }
}
