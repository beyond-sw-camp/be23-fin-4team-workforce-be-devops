package com._team._team.salary.service;

import com._team._team.salary.domain.TaxRate;
import com._team._team.salary.domain.enums.TaxType;
import com._team._team.salary.dto.resdto.TaxSummaryResDto;
import com._team._team.salary.repository.PayrollItemRepository;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.TaxRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4대보험 + 원천세 집계 서비스
@Service
@Transactional(readOnly = true)
public class TaxSummaryService {

    // PayrollCalculationService 가 채우는 항목명과 정확히 일치해야 집계가 됨
    private static final String NATIONAL_PENSION = "국민연금";
    private static final String HEALTH_INSURANCE = "건강보험";
    private static final String LONG_TERM_CARE = "장기요양보험";
    private static final String EMPLOYMENT_INSURANCE = "고용보험";
    private static final String INCOME_TAX = "소득세";
    private static final String LOCAL_INCOME_TAX = "지방소득세";

    private static final List<String> TARGET_ITEM_NAMES = List.of(
            NATIONAL_PENSION, HEALTH_INSURANCE, LONG_TERM_CARE,
            EMPLOYMENT_INSURANCE, INCOME_TAX, LOCAL_INCOME_TAX);

    private final PayrollItemRepository payrollItemRepository;
    private final PayrollRepository payrollRepository;
    private final TaxRateRepository taxRateRepository;

    @Autowired
    public TaxSummaryService(PayrollItemRepository payrollItemRepository, PayrollRepository payrollRepository, TaxRateRepository taxRateRepository) {
        this.payrollItemRepository = payrollItemRepository;
        this.payrollRepository = payrollRepository;
        this.taxRateRepository = taxRateRepository;
    }

    public TaxSummaryResDto getMonthlySummary(UUID companyId, YearMonth yearMonth) {
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        // 1) 직원 부담 항목별 SUM 한 쿼리
        List<Object[]> rows = payrollItemRepository
                .sumByItemNamesInMonth(companyId, from, to, TARGET_ITEM_NAMES);

        Map<String, Long> sumByName = new HashMap<>();
        for (Object[] row : rows) {
            String name = (String) row[0];
            long amount = ((Number) row[1]).longValue();
            sumByName.put(name, amount);
        }

        long nationalPension = sumByName.getOrDefault(NATIONAL_PENSION, 0L);
        long healthInsurance = sumByName.getOrDefault(HEALTH_INSURANCE, 0L);
        long longTermCare = sumByName.getOrDefault(LONG_TERM_CARE, 0L);
        long employmentInsurance = sumByName.getOrDefault(EMPLOYMENT_INSURANCE, 0L);
        long incomeTax = sumByName.getOrDefault(INCOME_TAX, 0L);
        long localIncomeTax = sumByName.getOrDefault(LOCAL_INCOME_TAX, 0L);

        // 2) 직원 수 + 보수월액 SUM (산재 추정용)
        long memberCount = payrollItemRepository.countMembersInMonth(companyId, from, to);
        long totalPayment = payrollRepository.sumTotalPaymentInMonth(companyId, from, to);

        // 3) TaxRate 조회 (조회 월 기준 연도)
        Map<TaxType, TaxRate> rateByType = loadTaxRates(yearMonth.getYear());

        // 4) 회사 부담 추정 비율 곱셈
        long npEmployer = estimateEmployer(nationalPension, rateByType.get(TaxType.NATIONAL_PENSION));
        long hiEmployer = estimateEmployer(healthInsurance, rateByType.get(TaxType.HEALTH_INSURANCE));
        long ltcEmployer = estimateEmployer(longTermCare, rateByType.get(TaxType.LONG_TERM_CARE));
        long eiEmployer = estimateEmployer(employmentInsurance, rateByType.get(TaxType.EMPLOYMENT_INSURANCE));

        // 5) 산재 회사 100 보수월액 SUM × 산재율
        long iaEmployer = estimateIndustrialAccident(totalPayment, rateByType.get(TaxType.ACCIDENT_INSURANCE));

        long fourEmployerTotal = npEmployer + hiEmployer + ltcEmployer + eiEmployer + iaEmployer;

        return TaxSummaryResDto.builder()
                .yearMonth(yearMonth.toString())
                .memberCount(memberCount)
                .nationalPension(nationalPension)
                .healthInsurance(healthInsurance)
                .longTermCare(longTermCare)
                .employmentInsurance(employmentInsurance)
                .nationalPensionEmployer(npEmployer)
                .healthInsuranceEmployer(hiEmployer)
                .longTermCareEmployer(ltcEmployer)
                .employmentInsuranceEmployer(eiEmployer)
                .industrialAccidentEmployer(iaEmployer)
                .incomeTax(incomeTax)
                .localIncomeTax(localIncomeTax)
                .fourInsuranceTotal(nationalPension + healthInsurance + longTermCare + employmentInsurance)
                .fourInsuranceEmployerTotal(fourEmployerTotal)
                .withholdingTotal(incomeTax + localIncomeTax)
                .build();
    }

    // 그 해 TaxRate 한 번에 조회 후 TaxType
    private Map<TaxType, TaxRate> loadTaxRates(int year) {
        Map<TaxType, TaxRate> map = new EnumMap<>(TaxType.class);
        List<TaxRate> rates = taxRateRepository.findByApplyYear(year);
        for (TaxRate r : rates) {
            // 가장 최근 등록 우선 또는 첫번째 사용
            map.putIfAbsent(r.getTaxType(), r);
        }
        return map;
    }

    // 직원 부담 × employerRate / employeeRate 비율 곱셈
    // 비율 정보 없으면 0 반환
    private long estimateEmployer(long employeeAmount, TaxRate rate) {
        if (rate == null
                || rate.getRate() == null
                || rate.getEmployerRate() == null
                || rate.getRate().compareTo(BigDecimal.ZERO) == 0) {
            return 0L;
        }
        return BigDecimal.valueOf(employeeAmount)
                .multiply(rate.getEmployerRate())
                .divide(rate.getRate(), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    // 산재 보수월액 SUM × 회사 산재율
    // 직원 부담 0 이라 비율 곱셈 안 됨 보수월액에 employerRate 직접 곱함
    // employerRate 는 이미 소수점 비율 형태 (예 0.0085 = 0.85%) 이므로 100 나누지 않음
    private long estimateIndustrialAccident(long totalPayment, TaxRate rate) {
        if (rate == null || rate.getEmployerRate() == null) {
            return 0L;
        }
        return BigDecimal.valueOf(totalPayment)
                .multiply(rate.getEmployerRate())
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }
}
