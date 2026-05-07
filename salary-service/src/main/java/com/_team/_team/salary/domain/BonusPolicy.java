package com._team._team.salary.domain;


import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.BonusEligibilityScope;
import com._team._team.salary.domain.enums.HolidayBonusType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 회사별 보너스 (상여/성과급/명절상여) 정책
 * 지급 룰 / 한도 / 대상 범위 저장
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        indexes = {
                @Index(name = "idx_bonus_policy_company_active",
                        columnList = "companyId, effectiveFrom")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_bonus_policy_company_effective_from",
                        columnNames = {"companyId", "effectiveFrom"}
                )
        }
)
public class BonusPolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID bonusPolicyId;

    @Column(nullable = false)
    private UUID companyId;

    /** 정기 상여 */
    // 정기상여 사용 여부 Y N
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String useRegularBonusYn = "N";

    /** 연 누계 비율 기본급 기준 */
    @Column(precision = 6, scale = 2)
    private BigDecimal regularBonusAnnualRate;

    /** 연 지급 횟수 분기 4 반기 2 명절포함 6 등 */
    private Integer regularBonusPaymentCount;

    /** 성과급 */
    /** 성과급 사용 여부 */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String usePerformanceBonusYn = "N";

    /** 1회 최대 지급 비율 기본급 기준 200 2배까지 의미 */
    @Column(precision = 6, scale = 2)
    private BigDecimal performanceBonusMaxRate;

    /** 산정 기준 메모 평가등급 매출 등 자유 텍스트 */
    @Column(length = 500)
    private String performanceBonusBasis;

    /** 명절상여 */
    /** 명절상여 사용 여부 */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String useHolidayBonusYn = "N";

    /** 지급 방식 RATE 비율 AMOUNT 정액 */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private HolidayBonusType holidayBonusType;

    /** 비율 또는 정액 holidayBonusType 에 따라 의미 달라짐 */
    @Column(precision = 12, scale = 2)
    private BigDecimal holidayBonusValue;

    /** 공통 */
    // 지급 대상 범위 ALL 전직원 REGULAR_ONLY 정규직만
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BonusEligibilityScope eligibilityScope = BonusEligibilityScope.ALL;

    /**
     * 최소 근속 월수 - 입사 후 N개월 미만 직원은 상여 대상에서 제외
     */
    @Column
    @Builder.Default
    private Integer minTenureMonths = 0;

    /**
     * 휴직자 제외 여부 - 'Y' 면 휴직 중인 직원은 상여 지급에서 제외
     */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String excludeOnLeaveYn = "Y";

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    // 정책 등록 시 비고 메모 변경 사유 등
    @Column(length = 500)
    private String memo;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    // 정책 마감 새 정책 등록 시 이전 활성 행 자동 종료용
    public void closeAt(LocalDate closingDate) {
        this.effectiveTo = closingDate;
    }

    // 정책 수정 effectiveFrom 은 변경 불가 나머지 항목 일괄 갱신
    public void update(String useRegularBonusYn,
                       BigDecimal regularBonusAnnualRate,
                       Integer regularBonusPaymentCount,
                       String usePerformanceBonusYn,
                       BigDecimal performanceBonusMaxRate,
                       String performanceBonusBasis,
                       String useHolidayBonusYn,
                       HolidayBonusType holidayBonusType,
                       BigDecimal holidayBonusValue,
                       BonusEligibilityScope eligibilityScope,
                       Integer minTenureMonths,
                       String excludeOnLeaveYn,
                       LocalDate effectiveTo,
                       String memo) {
        this.useRegularBonusYn = useRegularBonusYn;
        this.regularBonusAnnualRate = regularBonusAnnualRate;
        this.regularBonusPaymentCount = regularBonusPaymentCount;
        this.usePerformanceBonusYn = usePerformanceBonusYn;
        this.performanceBonusMaxRate = performanceBonusMaxRate;
        this.performanceBonusBasis = performanceBonusBasis;
        this.useHolidayBonusYn = useHolidayBonusYn;
        this.holidayBonusType = holidayBonusType;
        this.holidayBonusValue = holidayBonusValue;
        this.eligibilityScope = eligibilityScope;
        this.minTenureMonths = minTenureMonths != null ? minTenureMonths : 0;
        this.excludeOnLeaveYn = excludeOnLeaveYn != null ? excludeOnLeaveYn : "Y";
        this.effectiveTo = effectiveTo;
        this.memo = memo;
    }

    public void softDelete() {
        this.delYn = "Y";
    }

    // 특정 날짜 시점 활성 여부 판단
    public boolean isActiveAt(LocalDate date) {
        if ("Y".equals(delYn)) return false;
        if (effectiveFrom.isAfter(date)) return false;
        return effectiveTo == null || !effectiveTo.isBefore(date);
    }
}
