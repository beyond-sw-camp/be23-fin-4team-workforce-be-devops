package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.AccrualBase;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeavePolicyUpdateReqDto {

    /** 연차 촉진제도 사용 여부 */
    private String isPromotionYn;

    /** 1차 촉진 통보 기한 (일) */
    private Integer promotion1stBeforeDays;

    /** 2차 촉진 통보 기한 (일) */
    private Integer promotion2ndBeforeDays;

    /** 연차 이월 허용 여부 */
    private String isCarryoverYn;

    /** 이월 가능 일수 */
    private Integer carryoverDays;

    /** 이월 동의서 사용 여부 - 'Y' 면 직원별 동의 받아야 이월 처리 진행 */
    private String isCarryoverConsentYn;

    /** 미사용 연차 수당 지급 여부 */
    private String isPayoutYn;

    /** 연차 발생 기준 (FISCAL: 회계연도 기준, HIRE_DATE: 입사일 기준) */
    private AccrualBase accrualBase;

    /** 1년차 기본 부여 일수 근로기준법 최소 15일 */
    @DecimalMin(value = "15.0", message = "근로기준법상 1년차 연차는 최소 15일 이상이어야 합니다.")
    private Double defaultAnnualDays;

    /** 매 N년마다 추가 부여 단위 근로기준법 최소 1일 */
    @DecimalMin(value = "1.0", message = "근로기준법상 추가 일수는 최소 1일 이상이어야 합니다.")
    private Double extraDaysPerInterval;

    /** 추가 부여 주기 1~2 년 (근로기준법 최대 2년) */
    @Min(value = 1, message = "주기는 1년 이상이어야 합니다.")
    @Max(value = 2, message = "근로기준법상 추가 부여 주기는 최대 2년입니다.")
    private Integer extraIntervalYears;

    /** 연차 상한 근로기준법 최소 25일 */
    @DecimalMin(value = "25.0", message = "근로기준법상 연차 상한은 최소 25일 이상이어야 합니다.")
    private Double maxAnnualDays;
}
