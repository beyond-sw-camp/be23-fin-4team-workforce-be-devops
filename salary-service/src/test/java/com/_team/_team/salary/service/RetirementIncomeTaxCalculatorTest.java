package com._team._team.salary.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 퇴직소득세 분리과세 산출 검증
 * 한국 세법 (2024) 기준 누진 산식 정확도 단위 테스트
 */
class RetirementIncomeTaxCalculatorTest {

    @Test
    @DisplayName("0원 또는 근속 0일이면 세액 0")
    void zeroAmountOrZeroDaysReturnsZero() {
        var r1 = RetirementIncomeTaxCalculator.calculate(0L, 365L);
        var r2 = RetirementIncomeTaxCalculator.calculate(10_000_000L, 0L);
        assertEquals(0L, r1.incomeTax());
        assertEquals(0L, r2.incomeTax());
        assertEquals(0L, r1.localTax());
        assertEquals(0L, r2.localTax());
    }

    @Test
    @DisplayName("근속연수 1년 미만 단수는 1년으로 처리 (ceil)")
    void serviceYearsCeil() {
        // 366일 -> 2년 (ceil)
        var r = RetirementIncomeTaxCalculator.calculate(20_000_000L, 366L);
        assertEquals(2L, r.serviceYears());

        // 1일 -> 1년 (ceil)
        var r2 = RetirementIncomeTaxCalculator.calculate(20_000_000L, 1L);
        assertEquals(1L, r2.serviceYears());

        // 정확히 365일 -> 1년
        var r3 = RetirementIncomeTaxCalculator.calculate(20_000_000L, 365L);
        assertEquals(1L, r3.serviceYears());
    }

    @Test
    @DisplayName("3년 근속, 퇴직금 3000만 - 분리과세 산출")
    void mediumAmount3Years() {
        // 근속연수 3년, 퇴직금 3000만
        // 근속연수공제 = 100만 × 3 = 300만
        // 환산급여 = (3000만 - 300만) × 12 / 3 = 2700만 × 4 = 1억 800만 (108_000_000)
        // 환산급여공제 (1억 ~ 3억 구간):
        //   6170만 + (1억 800만 - 1억) × 45% = 6170만 + 800만 × 45% = 6170만 + 360만 = 6530만
        // 환산과세표준 = 1억 800만 - 6530만 = 4270만
        // 환산산출세액 (1400만~5000만, 15% / 누진공제 126만):
        //   = 4270만 × 15% - 126만 = 6,405,000 - 1,260,000 = 5,145,000
        // 퇴직소득세 = 5,145,000 × 3 / 12 = 1,286,250
        // 지방소득세 = 1,286,250 / 10 = 128,625
        var r = RetirementIncomeTaxCalculator.calculate(30_000_000L, 365L * 3);
        assertEquals(3L, r.serviceYears());
        assertEquals(3_000_000L, r.serviceYearDeduction());
        assertEquals(108_000_000L, r.convertedAnnual());
        assertEquals(65_300_000L, r.convertedDeduction());
        assertEquals(42_700_000L, r.convertedTaxable());
        assertEquals(5_145_000L, r.convertedTax());
        assertEquals(1_286_250L, r.incomeTax());
        assertEquals(128_625L, r.localTax());
    }

    @Test
    @DisplayName("10년 근속, 퇴직금 1억 - 누진세 더 높은 구간 검증")
    void largerAmount10Years() {
        // 근속연수 10년, 퇴직금 1억
        // 근속연수공제 = 500만 + 200만 × 5 = 1500만
        // 환산급여 = (1억 - 1500만) × 12 / 10 = 8500만 × 1.2 = 1억 200만 (102_000_000)
        // 환산급여공제 (1억 ~ 3억 구간):
        //   6170만 + (1억 200만 - 1억) × 45% = 6170만 + 200만 × 45% = 6170만 + 90만 = 6260만
        // 환산과세표준 = 1억 200만 - 6260만 = 3940만
        // 환산산출세액 (1400만~5000만, 15% / 누진공제 126만):
        //   = 3940만 × 15% - 126만 = 5,910,000 - 1,260,000 = 4,650,000
        // 퇴직소득세 = 4,650,000 × 10 / 12 = 3,875,000
        // 지방소득세 = 387,500
        var r = RetirementIncomeTaxCalculator.calculate(100_000_000L, 365L * 10);
        assertEquals(10L, r.serviceYears());
        assertEquals(15_000_000L, r.serviceYearDeduction());
        assertEquals(102_000_000L, r.convertedAnnual());
        assertEquals(62_600_000L, r.convertedDeduction());
        assertEquals(39_400_000L, r.convertedTaxable());
        assertEquals(4_650_000L, r.convertedTax());
        assertEquals(3_875_000L, r.incomeTax());
        assertEquals(387_500L, r.localTax());
    }

    @Test
    @DisplayName("환산과세표준 0 (저액 / 단기 근속) -> 세액 0")
    void smallAmountResultsInZeroTax() {
        // 1년 근속, 50만 퇴직금
        // 근속연수공제 = 100만 (이미 퇴직금보다 큼)
        // taxBase = max(0, 50만 - 100만) = 0
        // 환산급여 = 0
        // 세액 = 0
        var r = RetirementIncomeTaxCalculator.calculate(500_000L, 365L);
        assertEquals(0L, r.incomeTax());
        assertEquals(0L, r.localTax());
        assertEquals(0L, r.convertedTaxable());
    }

    @Test
    @DisplayName("지방소득세는 항상 산출세액의 10% (정수 절사)")
    void localTaxIs10Percent() {
        var r = RetirementIncomeTaxCalculator.calculate(50_000_000L, 365L * 5);
        assertEquals(r.incomeTax() / 10, r.localTax());
    }

    @Test
    @DisplayName("산출세액은 음수가 될 수 없음")
    void incomeTaxNonNegative() {
        var r = RetirementIncomeTaxCalculator.calculate(1_000_000L, 365L * 20);
        assertTrue(r.incomeTax() >= 0);
        assertTrue(r.localTax() >= 0);
    }
}
