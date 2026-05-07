package com._team._team.salary.service;

/**
 * 한국 세법 기준 퇴직소득세 (분리과세) 계산
 *
 * 흐름:
 *  1) 근속연수 = ceil(근속일수 / 365), 최소 1
 *  2) 근속연수공제 = 누진식 (5/10/20년 구간)
 *  3) 환산급여 = (퇴직급여 - 근속연수공제) * 12 / 근속연수
 *  4) 환산급여공제 = 누진식 (800만/7000만/1억/3억 구간)
 *  5) 환산과세표준 = 환산급여 - 환산급여공제
 *  6) 환산산출세액 = 환산과세표준 * 누진세율 - 누진공제
 *  7) 산출세액(소득세) = 환산산출세액 * 근속연수 / 12
 *  8) 지방소득세 = 산출세액 * 10%
 *
 * 누진세율 (종합소득세 2024 기준):
 *  1400 이하 6%
 *  1400 ~ 5000 15% (누진공제 126만)
 *  5000 ~ 8800 24% (576만)
 *  8800 ~ 1.5억 35% (1544만)
 *  1.5억 ~ 3억 38% (1994만)
 *  3억 ~ 5억 40% (2594만)
 *  5억 ~ 10억 42% (3594만)
 *  10억 초과 45% (6594만)
 *
 * 비고: 본 계산은 일반적 케이스. 임원 퇴직금 한도 / 명예퇴직금 / 비과세 분리 등 특수 케이스는 별도.
 */
public final class RetirementIncomeTaxCalculator {

    private RetirementIncomeTaxCalculator() {}

    /**
     * 퇴직소득세 산출
     *
     * @param retirementAmount 퇴직급여 (원)
     * @param serviceDays      근속일수
     * @return 산출 결과 (incomeTax / localTax / breakdown)
     */
    public static Result calculate(long retirementAmount, long serviceDays) {
        if (retirementAmount <= 0 || serviceDays <= 0) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // 1) 근속연수 - 1년 미만 단수는 1년으로 처리 (ceil)
        long serviceYears = (serviceDays + 364) / 365;

        // 2) 근속연수공제
        long serviceYearDeduction = calcServiceYearDeduction(serviceYears);

        // 3) 환산급여 - (퇴직금 - 근속연수공제) * 12 / 근속연수
        long taxBase = Math.max(0, retirementAmount - serviceYearDeduction);
        long convertedAnnual = taxBase * 12 / serviceYears;

        // 4) 환산급여공제
        long convertedDeduction = calcConvertedDeduction(convertedAnnual);

        // 5) 환산과세표준
        long convertedTaxable = Math.max(0, convertedAnnual - convertedDeduction);

        // 6) 환산산출세액
        long convertedTax = applyProgressiveRate(convertedTaxable);

        // 7) 산출세액 (퇴직소득세)
        long incomeTax = convertedTax * serviceYears / 12;
        if (incomeTax < 0) incomeTax = 0;

        // 8) 지방소득세
        long localTax = incomeTax / 10;

        return new Result(
                incomeTax,
                localTax,
                serviceYears,
                serviceYearDeduction,
                convertedAnnual,
                convertedDeduction,
                convertedTaxable,
                convertedTax
        );
    }

    private static long calcServiceYearDeduction(long years) {
        if (years <= 5) {
            return 1_000_000L * years;
        } else if (years <= 10) {
            return 5_000_000L + 2_000_000L * (years - 5);
        } else if (years <= 20) {
            return 15_000_000L + 2_500_000L * (years - 10);
        } else {
            return 40_000_000L + 3_000_000L * (years - 20);
        }
    }

    private static long calcConvertedDeduction(long convertedAnnual) {
        if (convertedAnnual <= 8_000_000L) {
            return convertedAnnual;
        } else if (convertedAnnual <= 70_000_000L) {
            return 8_000_000L + (convertedAnnual - 8_000_000L) * 60 / 100;
        } else if (convertedAnnual <= 100_000_000L) {
            return 45_200_000L + (convertedAnnual - 70_000_000L) * 55 / 100;
        } else if (convertedAnnual <= 300_000_000L) {
            return 61_700_000L + (convertedAnnual - 100_000_000L) * 45 / 100;
        } else {
            return 151_700_000L + (convertedAnnual - 300_000_000L) * 35 / 100;
        }
    }

    /**
     * 종합소득세 누진세율 적용 (2024 기준)
     */
    private static long applyProgressiveRate(long taxable) {
        if (taxable <= 0) return 0;
        if (taxable <= 14_000_000L) {
            return taxable * 6 / 100;
        } else if (taxable <= 50_000_000L) {
            return taxable * 15 / 100 - 1_260_000L;
        } else if (taxable <= 88_000_000L) {
            return taxable * 24 / 100 - 5_760_000L;
        } else if (taxable <= 150_000_000L) {
            return taxable * 35 / 100 - 15_440_000L;
        } else if (taxable <= 300_000_000L) {
            return taxable * 38 / 100 - 19_940_000L;
        } else if (taxable <= 500_000_000L) {
            return taxable * 40 / 100 - 25_940_000L;
        } else if (taxable <= 1_000_000_000L) {
            return taxable * 42 / 100 - 35_940_000L;
        } else {
            return taxable * 45 / 100 - 65_940_000L;
        }
    }

    /**
     * 산출 결과
     */
    public record Result(
            long incomeTax,             // 퇴직소득세 (원천징수액)
            long localTax,              // 지방소득세 (10%)
            long serviceYears,          // 적용 근속연수
            long serviceYearDeduction,  // 근속연수공제
            long convertedAnnual,       // 환산급여
            long convertedDeduction,    // 환산급여공제
            long convertedTaxable,      // 환산과세표준
            long convertedTax           // 환산산출세액
    ) {}
}
