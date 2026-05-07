package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.AccrualBase;
import com._team._team.attendance.dto.reqDto.LeavePolicyUpdateReqDto;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 연차 정책
 * - 촉진제도: 연차 소진 독려 알림 기한 설정 (근로기준법 제61조)
 * - 이월: 미사용 연차 다음 해 이월 여부/일수
 * - 미사용 수당: 소멸 연차에 대한 수당 지급 여부
 * - accrualBase: 연차 발생 기준 (회계연도 vs 입사일)
 */

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
public class LeavePolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID policyId;

    @Column(nullable = false)
    private UUID companyId;

    /** 촉진제도 사용 여부 (근로기준법 제61조 연차 사용 촉진) */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isPromotionYn = "N";

    /** 1차 촉진 알림 — 만료일 N일 전 (ex: 180일 전) */
    private Integer promotion1stBeforeDays;

    /** 2차 촉진 알림 — 만료일 N일 전 (ex: 90일 전) */
    private Integer promotion2ndBeforeDays;

    /** 이월 허용 여부 */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isCarryoverYn = "N";

    /** 최대 이월 일수 (isCarryoverYn = 'Y'일 때만 의미) */
    private Integer carryoverDays;

    /**
     * 이월 동의서 사용 여부 - 'Y' 면 매 회계연도 종료 시점에 직원별로 이월 동의 받음
     */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isCarryoverConsentYn = "N";

    /** 미사용 연차 수당 지급 여부 */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isPayoutYn = "N";

    /**
     * 기본 부여 일수 (회사 커스텀)
     * FISCAL 모드: 1/1 일괄 부여 시 이 값만큼 부여 , HIRE_DATE 모드: 입사 기념일에 이 값만큼 부여
     */
    @Column(nullable = false)
    @Builder.Default
    private Double defaultAnnualDays = 15.0;

    /**
     * 근속별 추가 부여 단위 근로기준법 60조 매 N년마다 + 이 만큼
     * 근로기준법 디폴트 = 1.0 일
     */
    @Column(nullable = false)
    @Builder.Default
    private Double extraDaysPerInterval = 1.0;

    /**
     * 근속별 추가 부여 주기 근로기준법 60조 매 2년마다
     * 근로기준법 디폴트 = 2 년
     * 0 또는 음수 입력 방지는 서비스 검증에서 처리
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer extraIntervalYears = 2;

    /**
     * 연차 상한 근로기준법 25일
     * 회사가 더 후하게 줄 수는 있어도 25 미만으로 줄일 수는 없음
     */
    @Column(nullable = false)
    @Builder.Default
    private Double maxAnnualDays = 25.0;

    /** 연차 발생 기준 (FISCAL: 회계연도 1/1 / HIRE_DATE: 입사일 기준) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccrualBase accrualBase;

    @Column(length = 1, nullable = false)
    @Builder.Default
    private String delYn = "N";

    /**
     * 근속 N년 시점의 연차 일수 계산
     *  근로기준법 60조 공식: defaultAnnualDays + floor((years-1)/interval) × extra
     *  결과는 maxAnnualDays 로 cap
     *  yearsOfService < 1 일 때는 월차 별도 처리 호출자가 분기
     */
    public double calculateAnnualDays(int yearsOfService) {
        if (yearsOfService < 1) return 0.0;
        int interval = (extraIntervalYears == null || extraIntervalYears <= 0) ? 2 : extraIntervalYears;
        double extra = (extraDaysPerInterval == null) ? 1.0 : extraDaysPerInterval;
        double base = (defaultAnnualDays == null) ? 15.0 : defaultAnnualDays;
        double cap = (maxAnnualDays == null) ? 25.0 : maxAnnualDays;
        int extraSteps = Math.max(0, (yearsOfService - 1) / interval);
        double days = base + extraSteps * extra;
        return Math.min(days, cap);
    }

    public void update(LeavePolicyUpdateReqDto reqDto) {
        if (reqDto.getIsPromotionYn() != null) {
            this.isPromotionYn = reqDto.getIsPromotionYn();
        }
        if (reqDto.getPromotion1stBeforeDays() != null) {
            this.promotion1stBeforeDays = reqDto.getPromotion1stBeforeDays();
        }
        if (reqDto.getPromotion2ndBeforeDays() != null) {
            this.promotion2ndBeforeDays = reqDto.getPromotion2ndBeforeDays();
        }
        if (reqDto.getIsCarryoverYn() != null) {
            this.isCarryoverYn = reqDto.getIsCarryoverYn();
        }
        if (reqDto.getCarryoverDays() != null) {
            this.carryoverDays = reqDto.getCarryoverDays();
        }
        if (reqDto.getIsCarryoverConsentYn() != null) {
            this.isCarryoverConsentYn = reqDto.getIsCarryoverConsentYn();
        }
        if (reqDto.getIsPayoutYn() != null) {
            this.isPayoutYn = reqDto.getIsPayoutYn();
        }
        if (reqDto.getDefaultAnnualDays() != null) {
            this.defaultAnnualDays = reqDto.getDefaultAnnualDays();
        }
        if (reqDto.getExtraDaysPerInterval() != null) {
            this.extraDaysPerInterval = reqDto.getExtraDaysPerInterval();
        }
        if (reqDto.getExtraIntervalYears() != null && reqDto.getExtraIntervalYears() > 0) {
            this.extraIntervalYears = reqDto.getExtraIntervalYears();
        }
        if (reqDto.getMaxAnnualDays() != null) {
            this.maxAnnualDays = reqDto.getMaxAnnualDays();
        }
        if (reqDto.getAccrualBase() != null) {
            this.accrualBase = reqDto.getAccrualBase();
        }
    }

    public void delete() {
        this.delYn = "Y";
    }
}
