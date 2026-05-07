package com._team._team.salary.dto.resdto;


import com._team._team.salary.domain.enums.RetirementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

// 퇴직금 시뮬 응답
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RetirementSimResDto {

    // 회사 정책
    private RetirementType retirementType;
    private String modeDescription;

    // 직원 정보
    private UUID memberId;
    private String memberName;
    private LocalDate joinDate;        // 정산 시작일
    private LocalDate resignDate;      // 예상 퇴직일

    // 근속 / 급여
    private long serviceDays;          // 재직일수
    private long avgMonthlyWage;       // 평균 월급 (호환성 유지)

    // 평균임금 정확 산정, 근로기준법 제2조 1항 6호
    // 기본 일평균 = 직전 3개월 정기급여 임금총액 / 3개월 일수
    private long basePeriodPayment;    // 직전 3개월 정기급여 임금총액
    private int basePeriodDays;        // 직전 3개월 일수 (89~92)
    private long simpleDailyAverage;   // 단순 일평균 (가산 전)

    // 시행령 제2조 평균임금 산정 제외 기간
    // 출산휴가 / 육아휴직 / 산재요양 / 병역 / 쟁의행위 등 휴직 기간
    private int excludedLeaveDays;     // 3개월 기간 내 휴직 일수 합계
    private int excludedLeaveCount;    // 제외된 휴직 건수
    private int adjustedPeriodDays;    // basePeriodDays - excludedLeaveDays (실 분모)

    // 직전 12개월 상여금 / 연차수당 가산분
    // 한국 노동법 표준 12개월 합계 × 3/12 = 3개월 환산분
    private long bonusAddition12mAvg;  // 12개월 상여 환산 (3/12)
    private long unusedLeaveAddition12mAvg; // 12개월 연차수당 환산 (3/12)

    // 평균임금 일액 = simpleDailyAverage + (가산금 / basePeriodDays)
    private long averageDailyWage;
    // 통상시급 일액 = ordinaryWage × 8 / monthlyOrdinaryHours
    private long ordinaryDailyWage;
    // 적용 평균임금 시행령 제2조 max(평균, 통상)
    private long appliedDailyWage;
    // 적용 기준 표시용 (AVERAGE / ORDINARY)
    private String appliedBasis;

    // 결과
    private long estimatedAmount;      // 예상 퇴직금
    private boolean eligible;          // 1년 이상 자격 충족 여부

    // 안내
    private String disclaimer;
}