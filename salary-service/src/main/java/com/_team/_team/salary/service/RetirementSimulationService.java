package com._team._team.salary.service;

import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.domain.enums.LeaveOfAbsenceApprovalStatus;
import com._team._team.attendance.repository.MemberLeaveOfAbsenceRepository;
import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.RetirementPolicy;
import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.PayrollType;
import com._team._team.salary.domain.enums.RetirementType;
import com._team._team.salary.domain.vo.AutoPayrollItem;
import com._team._team.salary.dto.reqdto.RetirementSimReqDto;
import com._team._team.salary.dto.resdto.RetirementSimResDto;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.repository.MemberAllowanceRepository;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.RetirementPolicyRepository;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 퇴직금 시뮬레이션 회사 정책 LEGAL DB DC 분기
 *  법정 퇴직금 DB == 평균임금 일액 × 30 × 근속일수 365
 *  DC == 평균월급 × 근속연수 매월 1 12 누적 근사
 *
 * 평균임금 정확 산정 (근로기준법 제2조 1항 6호 + 시행령 제2조)
 *  기본급 직전 3개월 정기급여 임금총액 / 3개월 calendar 일수
 *  + 직전 12개월 상여금 합계 × 3/12 환산
 *  + 직전 12개월 연차수당 합계 × 3/12 환산
 *  -> 일평균 = (base + 환산) / 3개월일수
 *  -> 통상시급 일액 = ordinaryWage × 8 / monthlyOrdinaryHours
 *  -> 적용 평균임금 = max(일평균, 통상시급일액) 시행령 제2조
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class RetirementSimulationService {

    private final RetirementPolicyRepository retirementPolicyRepository;
    private final SalaryRepository salaryRepository;
    private final SalaryPolicyRepository salaryPolicyRepository;
    private final SalaryItemTemplateRepository salaryItemTemplateRepository;
    private final MemberAllowanceRepository memberAllowanceRepository;
    private final PayrollRepository payrollRepository;
    private final MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository;
    private final MemberFeignClient memberFeignClient;
    private final PayrollCalculationService payrollCalculationService;

    @Autowired
    public RetirementSimulationService(RetirementPolicyRepository retirementPolicyRepository,
                                       SalaryRepository salaryRepository,
                                       SalaryPolicyRepository salaryPolicyRepository,
                                       SalaryItemTemplateRepository salaryItemTemplateRepository,
                                       MemberAllowanceRepository memberAllowanceRepository,
                                       PayrollRepository payrollRepository,
                                       MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository,
                                       MemberFeignClient memberFeignClient,
                                       PayrollCalculationService payrollCalculationService) {
        this.retirementPolicyRepository = retirementPolicyRepository;
        this.salaryRepository = salaryRepository;
        this.salaryPolicyRepository = salaryPolicyRepository;
        this.salaryItemTemplateRepository = salaryItemTemplateRepository;
        this.memberAllowanceRepository = memberAllowanceRepository;
        this.payrollRepository = payrollRepository;
        this.memberLeaveOfAbsenceRepository = memberLeaveOfAbsenceRepository;
        this.memberFeignClient = memberFeignClient;
        this.payrollCalculationService = payrollCalculationService;
    }

    public RetirementSimResDto simulateForMember(UUID companyId, UUID memberId, RetirementSimReqDto req) {
        // 회사 정책 조회 없으면 LEGAL 기본
        RetirementPolicy policy = retirementPolicyRepository
                .findActiveAt(companyId, LocalDate.now())
                .orElse(null);
        RetirementType type = policy != null ? policy.getRetirementType() : RetirementType.LEGAL;

        // 입사일 결정 요청에 있으면 우선 사용 없으면 member-service 에서 조회
        LocalDate joinDate = req.getJoinDate();
        if (joinDate == null) {
            joinDate = fetchJoinDate(companyId, memberId);
        }
        if (joinDate == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "정산 시작일을 확인할 수 없습니다 (입사일 미등록).");
        }
        long days = ChronoUnit.DAYS.between(joinDate, req.getResignDate());
        if (days < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "퇴직일이 입사일보다 빠릅니다.");
        }
        long years = days / 365;
        boolean eligible = days >= 365;

        // 활성 Salary baseSalary 호환성 유지용 평균월급
        long baseMonthly = salaryRepository
                .findActiveSalary(memberId, companyId, LocalDate.now())
                .map(Salary::getBaseSalary)
                .orElse(0L);

        // 평균임금 정확 산정 breakdown
        AverageWage aw = computeAverageWage(companyId, memberId, req.getResignDate(), baseMonthly);

        // 모드별 계산 1년 미만이면 0 LEGAL DB 는 일액 기준 DC 는 월 부담금 × 12 × 근속연수
        long estimated;
        if (!eligible) {
            estimated = 0L;
        } else if (type == RetirementType.DC) {
            // 정책 dcContributionRate 미설정 시 법정 기본 8.33% (1/12)
            BigDecimal rate = policy != null && policy.getDcContributionRate() != null
                    ? policy.getDcContributionRate()
                    : new BigDecimal("8.33");
            // baseMonthly × rate% × 12 × years
            estimated = rate
                    .multiply(BigDecimal.valueOf(baseMonthly))
                    .multiply(BigDecimal.valueOf(12L * years))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();
        } else {
            // LEGAL DB 적용 일평균 × 30 × 근속일수 / 365
            estimated = aw.appliedDailyWage * 30 * days / 365;
        }

        log.info("[RETIREMENT-SIM] memberId={} type={} days={} baseMonthly={} appliedDaily={} estimated={}",
                memberId, type, days, baseMonthly, aw.appliedDailyWage, estimated);

        return RetirementSimResDto.builder()
                .retirementType(type)
                .modeDescription(describeMode(type))
                .memberId(memberId)
                .memberName(null)
                .joinDate(joinDate)
                .resignDate(req.getResignDate())
                .serviceDays(days)
                .avgMonthlyWage(baseMonthly)
                .basePeriodPayment(aw.basePeriodPayment)
                .basePeriodDays(aw.basePeriodDays)
                .simpleDailyAverage(aw.simpleDailyAverage)
                .excludedLeaveDays(aw.excludedLeaveDays)
                .excludedLeaveCount(aw.excludedLeaveCount)
                .adjustedPeriodDays(aw.adjustedPeriodDays)
                .bonusAddition12mAvg(aw.bonusAddition12mAvg)
                .unusedLeaveAddition12mAvg(aw.unusedLeaveAddition12mAvg)
                .averageDailyWage(aw.averageDailyWage)
                .ordinaryDailyWage(aw.ordinaryDailyWage)
                .appliedDailyWage(aw.appliedDailyWage)
                .appliedBasis(aw.appliedBasis)
                .estimatedAmount(estimated)
                .eligible(eligible)
                .disclaimer(disclaimer(type))
                .build();
    }

    /**
     * 사직 결재 승인 cascade 시 자동 호출 - 퇴직정산 Payroll 생성
     * - 항목 구성:
     *   1. 퇴직월 일할 급여 (해당월 REGULAR_MONTHLY 미생성 시 baseMonthly × 근무일/월일수)
     *   2. 퇴직금 (LEGAL/DB/DC simulate 결과)
     *   3. 미사용 연차 수당 (잠금 직전 잔여 연차일수 × 통상시급 일액) - unusedLeaveDays > 0 일 때만
     */
    @Transactional
    public Payroll createRetirementSettlementPayroll(UUID companyId,
                                                      UUID memberId,
                                                      LocalDate resignDate,
                                                      Double unusedLeaveDays) {
        // 중복 방지 - 이미 같은 날 RETIREMENT_SETTLEMENT 있으면 그대로 반환
        Payroll existing = payrollRepository
                .findByCompanyIdAndMemberIdAndPayrollYearMonthDay(
                        companyId, memberId, resignDate)
                .filter(p -> "N".equals(p.getDelYn()))
                .filter(p -> p.getPayrollType() == PayrollType.RETIREMENT_SETTLEMENT)
                .orElse(null);
        if (existing != null) {
            log.info("[RETIREMENT-SETTLEMENT] 이미 생성됨 skip memberId={} resignDate={}",
                    memberId, resignDate);
            return existing;
        }

        // 시뮬레이션 호출
        RetirementSimReqDto req = new RetirementSimReqDto();
        req.setResignDate(resignDate);
        RetirementSimResDto sim = simulateForMember(companyId, memberId, req);

        long retirementAmount = sim.getEstimatedAmount();

        // 미사용 연차 수당 = 통상시급 일액 × 잔여 연차일수
        long unusedLeaveAmount = 0L;
        long unusedLeaveDaysLong = 0L;
        if (unusedLeaveDays != null && unusedLeaveDays > 0 && sim.getOrdinaryDailyWage() > 0) {
            unusedLeaveDaysLong = Math.round(unusedLeaveDays);
            unusedLeaveAmount = sim.getOrdinaryDailyWage() * unusedLeaveDaysLong;
        }

        // 퇴직월 일할 정산 - 한국 관행 자동 회수/추가지급 처리
        // 시나리오:
        //   (a) 해당월(targetYearMonth) REGULAR_MONTHLY 없음 -> 일할분 신규 EARNING 추가
        //   (b) 해당월 REGULAR_MONTHLY 이미 있음 -> 정기급여가 풀 지급(31/31 가정) 됐으므로
        //       실제 근무일(예: 28/31)과의 차이만큼 회수 또는 추가지급
        //         차액 = baseMonthly × daysWorked/daysInMonth - 이미지급된기본급(=baseMonthly 가정)
        //         음수: DEDUCTION "퇴직월 선급 정산 회수" - 회사가 직원에게서 회수
        //         양수: EARNING "퇴직월 일할 정산 추가" - 회사가 직원에게 추가지급
        // 회사 payCycleType 무관하게 targetYearMonth 로 매칭 (지급일이 6월이라도 5월분이면 매칭)
        long proratedEarning = 0L;       // EARNING (양수만, 신규 일할 또는 추가지급)
        long proratedRecovery = 0L;      // DEDUCTION (양수만, 선급 회수액)
        int proratedDaysWorked = 0;
        int proratedDaysInMonth = 0;
        long baseMonthlyForProrate = sim.getAvgMonthlyWage();
        String resignYmStr = YearMonth.from(resignDate).toString();
        boolean hasRegularThisMonth = payrollRepository
                .findByTargetYearMonthRangeFetchItems(companyId, memberId, resignYmStr, resignYmStr)
                .stream()
                .anyMatch(p -> p.getPayrollType() == null
                        || p.getPayrollType() == PayrollType.REGULAR_MONTHLY);
        if (baseMonthlyForProrate > 0) {
            proratedDaysWorked = resignDate.getDayOfMonth();
            proratedDaysInMonth = resignDate.lengthOfMonth();
            long expectedProrated = baseMonthlyForProrate * proratedDaysWorked / proratedDaysInMonth;
            if (!hasRegularThisMonth) {
                // 정기급여 미생성 - 일할분 신규 EARNING
                proratedEarning = expectedProrated;
            } else {
                // 정기급여 이미 풀 지급 - 차액 산정 (음수=회수, 양수=추가지급)
                long delta = expectedProrated - baseMonthlyForProrate;
                if (delta < 0) {
                    proratedRecovery = -delta;
                } else if (delta > 0) {
                    proratedEarning = delta;
                }
            }
        }

        long totalPayment = retirementAmount + unusedLeaveAmount + proratedEarning;

        // 활성 Salary 조회 - effectiveTo 가 resignDate 또는 그 이후
        Salary salary = salaryRepository
                .findActiveSalary(memberId, companyId, resignDate)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "퇴직정산 대상 직원의 활성 급여를 찾을 수 없습니다. memberId=" + memberId));

        // 1) 퇴직금 분리과세 - 퇴직소득세/지방소득세 자체 계산
        long days = ChronoUnit.DAYS.between(sim.getJoinDate(), resignDate);
        RetirementIncomeTaxCalculator.Result retiTax =
                RetirementIncomeTaxCalculator.calculate(retirementAmount, days);

        // 2) 일반 근로소득 (미사용연차 + 일할 급여) - 4대보험 + 소득세 + 지방세 일괄 계산
        // 일반 근로소득 과세 대상 = 미사용 연차 수당 + 일할 EARNING (회수 DEDUCTION 은 제외)
        long ordinaryTaxableEarning = unusedLeaveAmount + proratedEarning;
        int dependents = salary.getDependentCount() != null ? salary.getDependentCount() : 1;
        int childUnder20 = salary.getChildUnder20Count() != null ? salary.getChildUnder20Count() : 0;
        List<AutoPayrollItem> ordinaryDeductions = ordinaryTaxableEarning > 0
                ? payrollCalculationService.calculateDeductions(
                        ordinaryTaxableEarning, resignDate,
                        dependents, childUnder20, salary.getTaxReductionRate())
                : List.of();

        long ordinaryDeductionSum = ordinaryDeductions.stream()
                .mapToLong(AutoPayrollItem::amount).sum();
        long totalDeduction = retiTax.incomeTax() + retiTax.localTax() + ordinaryDeductionSum + proratedRecovery;
        long netPay = totalPayment - totalDeduction;

        Payroll payroll = Payroll.builder()
                .companyId(companyId)
                .memberId(memberId)
                .salaryId(salary.getSalaryId())
                .payrollYearMonthDay(resignDate)
                .targetYearMonth(YearMonth.from(resignDate).toString())
                .totalPayment(totalPayment)
                .totalDeduction(totalDeduction)
                .netPay(netPay)
                .payrollType(PayrollType.RETIREMENT_SETTLEMENT)
                .build();
        Payroll saved = payrollRepository.save(payroll);

        int order = 1;

        // 퇴직월 일할 EARNING - 신규 일할 (정기급여 미생성 시) 또는 추가 지급 차액 (정기급여 < 일할 가정)
        if (proratedEarning > 0) {
            String label = hasRegularThisMonth
                    ? String.format("퇴직월 일할 정산 추가 (%d/%d일)", proratedDaysWorked, proratedDaysInMonth)
                    : String.format("퇴직월 일할 급여 (%d/%d일)", proratedDaysWorked, proratedDaysInMonth);
            PayrollItem proratedItem = PayrollItem.builder()
                    .payroll(saved)
                    .itemName(label)
                    .itemType(ItemType.EARNING)
                    .amount(proratedEarning)
                    .displayOrder(order++)
                    .isTaxableYn("Y")
                    .build();
            saved.getPayrollItemList().add(proratedItem);
        }

        // 퇴직금 항목 - 분리과세이므로 일반 소득세 비과세
        PayrollItem retirementItem = PayrollItem.builder()
                .payroll(saved)
                .itemName("퇴직금")
                .itemType(ItemType.EARNING)
                .amount(retirementAmount)
                .displayOrder(order++)
                .isTaxableYn("N")
                .build();
        saved.getPayrollItemList().add(retirementItem);

        // 미사용 연차 수당 항목 - 잔여일수 > 0 일 때만 추가
        // 일반 소득세 과세 대상 (퇴직금과 별도)
        if (unusedLeaveAmount > 0) {
            PayrollItem leavePayoutItem = PayrollItem.builder()
                    .payroll(saved)
                    .itemName(String.format("미사용 연차 수당 (%d일)", unusedLeaveDaysLong))
                    .itemType(ItemType.EARNING)
                    .amount(unusedLeaveAmount)
                    .displayOrder(order++)
                    .isTaxableYn("Y")
                    .build();
            saved.getPayrollItemList().add(leavePayoutItem);
        }

        // 공제 항목 표시 순서는 200번대 (일반 급여 관행과 일치)
        int deductionOrder = 200;

        // 퇴직월 선급 정산 회수 - 정기급여 풀(31/31) 지급된 상태에서 일찍 퇴사 시 환수
        if (proratedRecovery > 0) {
            PayrollItem recoveryItem = PayrollItem.builder()
                    .payroll(saved)
                    .itemName(String.format("퇴직월 선급 정산 회수 (%d/%d일 근무, %d일치)",
                            proratedDaysWorked, proratedDaysInMonth,
                            proratedDaysInMonth - proratedDaysWorked))
                    .itemType(ItemType.DEDUCTION)
                    .amount(proratedRecovery)
                    .displayOrder(deductionOrder++)
                    .isTaxableYn("Y")
                    .build();
            saved.getPayrollItemList().add(recoveryItem);
        }

        // 일반 근로소득 공제 (4대보험 + 소득세 + 지방소득세)
        for (AutoPayrollItem auto : ordinaryDeductions) {
            PayrollItem item = PayrollItem.builder()
                    .payroll(saved)
                    .itemName(auto.itemName())
                    .itemType(auto.itemType())
                    .amount(auto.amount())
                    .displayOrder(auto.displayOrder() > 0 ? auto.displayOrder() : deductionOrder++)
                    .isTaxableYn(auto.isTaxableYn())
                    .build();
            saved.getPayrollItemList().add(item);
        }

        // 퇴직소득세 (분리과세) - 퇴직금만 대상
        if (retiTax.incomeTax() > 0) {
            PayrollItem retiTaxItem = PayrollItem.builder()
                    .payroll(saved)
                    .itemName("퇴직소득세")
                    .itemType(ItemType.DEDUCTION)
                    .amount(retiTax.incomeTax())
                    .displayOrder(290)
                    .isTaxableYn("Y")
                    .build();
            saved.getPayrollItemList().add(retiTaxItem);
        }

        // 퇴직 지방소득세 - 퇴직소득세의 10%
        if (retiTax.localTax() > 0) {
            PayrollItem retiLocalTaxItem = PayrollItem.builder()
                    .payroll(saved)
                    .itemName("퇴직 지방소득세")
                    .itemType(ItemType.DEDUCTION)
                    .amount(retiTax.localTax())
                    .displayOrder(291)
                    .isTaxableYn("Y")
                    .build();
            saved.getPayrollItemList().add(retiLocalTaxItem);
        }

        log.info("[RETIREMENT-SETTLEMENT] 생성 memberId={} resignDate={} type={} proratedEarning={} proratedRecovery={} ({}일/{}) retirement={} unusedLeave={}일/{} retiTax={}/{} ordinaryDed={} netPay={} eligible={}",
                memberId, resignDate, sim.getRetirementType(),
                proratedEarning, proratedRecovery,
                proratedDaysWorked, proratedDaysInMonth,
                retirementAmount, unusedLeaveDaysLong, unusedLeaveAmount,
                retiTax.incomeTax(), retiTax.localTax(),
                ordinaryDeductionSum, netPay, sim.isEligible());
        return saved;
    }

    /**
     * 평균임금 정확 산정 한국 근로기준법 제2조 1항 6호 + 시행령 제2조
     *  데이터 부족 시 baseMonthly fallback
     */
    private AverageWage computeAverageWage(UUID companyId,
                                           UUID memberId,
                                           LocalDate resignDate,
                                           long baseMonthly) {
        AverageWage aw = new AverageWage();

        // 정산 대상 월 기준 - 회사의 payCycleType 무관하게 "근무한 월" 기준으로 묶음.
        // 한국 평균임금 산정 = 사유 발생일 직전 3개월 임금총액 / 90일 일반화.
        // YearMonth 기반 묶음으로 회사간 지급주기 차이(당월/전월)를 흡수.
        YearMonth resignYm = YearMonth.from(resignDate);
        YearMonth threeYmFrom = resignYm.minusMonths(3);  // 사유발생월 직전 3개월
        YearMonth threeYmTo = resignYm.minusMonths(1);

        // 1) 직전 3개월 정기급여 (REGULAR_MONTHLY) Payroll 임금총액
        aw.basePeriodDays = threeYmFrom.lengthOfMonth()
                + threeYmFrom.plusMonths(1).lengthOfMonth()
                + threeYmTo.lengthOfMonth();

        List<Payroll> recentPayrolls = payrollRepository.findByTargetYearMonthRangeFetchItems(
                companyId, memberId, threeYmFrom.toString(), threeYmTo.toString());

        // 정기급여만 합산 (보너스 별도 가산)
        long regularPayment = recentPayrolls.stream()
                .filter(p -> p.getPayrollType() == null || p.getPayrollType() == PayrollType.REGULAR_MONTHLY)
                .mapToLong(p -> p.getTotalPayment() == null ? 0L : p.getTotalPayment())
                .sum();
        aw.basePeriodPayment = regularPayment;

        // 데이터 0 이면 baseMonthly × 3 로 fallback (신규 도입 회사 / targetYearMonth 미마이그레이션 데이터)
        if (regularPayment <= 0) {
            regularPayment = baseMonthly * 3;
            aw.basePeriodPayment = regularPayment;
        }

        // 1-1) 시행령 제2조 평균임금 산정 제외 기간 계산
        //      ACTIVE / ENDED 상태 휴직 중 3개월 기간과 겹치는 일수 합산
        //      출산휴가 / 육아휴직 / 산재요양 / 병역 / 쟁의행위 등이 해당
        // 3개월 범위 = threeYmFrom.atDay(1) ~ threeYmTo.atEndOfMonth()
        LocalDate threeRangeStart = threeYmFrom.atDay(1);
        LocalDate threeRangeEnd = threeYmTo.atEndOfMonth();
        List<MemberLeaveOfAbsence> loas = memberLeaveOfAbsenceRepository
                .findInPeriod(companyId, memberId, resignDate);

        int excludedDays = 0;
        int excludedCount = 0;
        for (MemberLeaveOfAbsence loa : loas) {
            // 신청 대기 / 반려 / 취소 상태는 무시 - findInPeriod 가 ACTIVE / ENDED 만 반환
            LocalDate effectiveEnd =
                    loa.getStatus() == LeaveOfAbsenceApprovalStatus.ENDED
                            && loa.getActualEndDate() != null
                            ? loa.getActualEndDate()
                            : loa.getEndDate();

            // 3개월 기간 밖이면 제외
            if (effectiveEnd.isBefore(threeRangeStart)) continue;
            if (loa.getStartDate().isAfter(threeRangeEnd)) continue;

            LocalDate overlapStart = loa.getStartDate().isBefore(threeRangeStart)
                    ? threeRangeStart : loa.getStartDate();
            LocalDate overlapEnd = effectiveEnd.isAfter(threeRangeEnd)
                    ? threeRangeEnd : effectiveEnd;

            if (!overlapStart.isAfter(overlapEnd)) {
                excludedDays += (int) ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
                excludedCount++;
            }
        }
        // 분모 일수 캡 (전체 제외되지 않도록)
        excludedDays = Math.min(excludedDays, aw.basePeriodDays - 1);
        excludedDays = Math.max(0, excludedDays);
        aw.excludedLeaveDays = excludedDays;
        aw.excludedLeaveCount = excludedCount;
        aw.adjustedPeriodDays = aw.basePeriodDays - excludedDays;

        // 단순 일평균은 조정된 일수 기준 (휴직 제외)
        aw.simpleDailyAverage = aw.adjustedPeriodDays > 0
                ? regularPayment / aw.adjustedPeriodDays
                : 0;

        // 2) 직전 12개월 상여 환산 합계 × 3/12
        YearMonth yearYmFrom = resignYm.minusMonths(12);
        YearMonth yearYmTo = resignYm.minusMonths(1);
        List<Payroll> yearlyPayrolls = payrollRepository.findByTargetYearMonthRangeFetchItems(
                companyId, memberId, yearYmFrom.toString(), yearYmTo.toString());

        long bonusYearlySum = yearlyPayrolls.stream()
                .filter(p -> p.getPayrollType() == PayrollType.PERFORMANCE_BONUS
                          || p.getPayrollType() == PayrollType.SPECIAL_BONUS)
                .mapToLong(p -> p.getTotalPayment() == null ? 0L : p.getTotalPayment())
                .sum();
        aw.bonusAddition12mAvg = bonusYearlySum * 3 / 12;

        // 3) 연차수당 환산 항목명 매칭 (연차수당 / 미사용연차수당 등)
        long unusedLeaveYearlySum = yearlyPayrolls.stream()
                .flatMap(p -> p.getPayrollItemList() == null
                        ? java.util.stream.Stream.empty()
                        : p.getPayrollItemList().stream())
                .filter(i -> i.getItemType() == ItemType.EARNING)
                .filter(i -> {
                    String name = i.getItemName() == null ? "" : i.getItemName();
                    return name.contains("연차수당") || name.contains("연차 수당")
                            || name.contains("미사용연차") || name.contains("미사용 연차");
                })
                .mapToLong(i -> i.getAmount() == null ? 0L : i.getAmount())
                .sum();
        aw.unusedLeaveAddition12mAvg = unusedLeaveYearlySum * 3 / 12;

        // 4) 평균임금 일액 = (3개월 임금총액 + 환산금) / 조정된 일수 (휴직 제외)
        long totalForAverage = aw.basePeriodPayment + aw.bonusAddition12mAvg + aw.unusedLeaveAddition12mAvg;
        aw.averageDailyWage = aw.adjustedPeriodDays > 0 ? totalForAverage / aw.adjustedPeriodDays : 0;

        // 5) 통상시급 일액 = ordinaryWage × 8 / monthlyOrdinaryHours
        // 활성 SalaryPolicy 의 monthlyOrdinaryHours 사용 없으면 209 fallback
        SalaryPolicy activeSalaryPolicy = salaryPolicyRepository
                .findActivePolicies(companyId, LocalDate.now())
                .stream().findFirst().orElse(null);
        int monthlyHours = activeSalaryPolicy != null && activeSalaryPolicy.getMonthlyOrdinaryHours() != null
                && activeSalaryPolicy.getMonthlyOrdinaryHours() > 0
                ? activeSalaryPolicy.getMonthlyOrdinaryHours() : 209;

        // 통상임금 산정 baseSalary + 직원별 활성 MemberAllowance 중 통상임금 플래그 Y 항목 합산
        long ordinaryWage = estimateOrdinaryWage(companyId, memberId, baseMonthly, resignDate);
        aw.ordinaryDailyWage = monthlyHours > 0
                ? (ordinaryWage * 8) / monthlyHours
                : 0;

        // 6) 시행령 제2조 max(평균, 통상)
        if (aw.averageDailyWage >= aw.ordinaryDailyWage) {
            aw.appliedDailyWage = aw.averageDailyWage;
            aw.appliedBasis = "AVERAGE";
        } else {
            aw.appliedDailyWage = aw.ordinaryDailyWage;
            aw.appliedBasis = "ORDINARY";
        }

        return aw;
    }

    /**
     * 통상임금 산정
     *  - baseSalary + 직원별 활성 MemberAllowance 중 통상임금 플래그 Y 인 항목들의 실제 금액 합
     *  - SalaryItemTemplate 의 isOrdinaryWageYn='Y' 인 templateId 만 통상임금 대상
     *  - resignDate 시점에 active 인 (effectiveFrom <= resignDate <= effectiveTo) 수당만 집계
     */
    private long estimateOrdinaryWage(UUID companyId, UUID memberId, long baseMonthly, LocalDate resignDate) {
        // 회사 통상임금 대상 templateId 셋
        Set<UUID> ordinaryTemplateIds = salaryItemTemplateRepository
                .findByCompanyIdAndDelYn(companyId, "N").stream()
                .filter(t -> t.getItemType() == ItemType.EARNING)
                .filter(t -> "Y".equals(t.getIsOrdinaryWageYn()))
                .map(SalaryItemTemplate::getSalaryItemTemplateId)
                .collect(java.util.stream.Collectors.toSet());

        if (ordinaryTemplateIds.isEmpty()) {
            return baseMonthly;
        }

        // 직원의 활성 수당 중 통상임금 대상 항목만 합
        long ordinaryAddition = memberAllowanceRepository
                .findActiveByMemberAndDate(memberId, companyId, resignDate).stream()
                .filter(a -> a.getSalaryItemTemplateId() != null
                        && ordinaryTemplateIds.contains(a.getSalaryItemTemplateId()))
                .mapToLong(a -> a.getAmount() == null ? 0L : a.getAmount())
                .sum();

        return baseMonthly + ordinaryAddition;
    }

    // 회사 직원 일괄 조회, 본인 joinDate 추출, Feign 실패 시 null 반환 graceful
    private LocalDate fetchJoinDate(UUID companyId, UUID memberId) {
        try {
            var res = memberFeignClient.getMembersByCompany(companyId);
            if (res == null || res.getData() == null) return null;
            return res.getData().stream()
                    .filter(m -> memberId.equals(m.getMemberId()))
                    .map(MemberResDto::getJoinDate)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[RETIREMENT-SIM] 입사일 조회 실패 companyId={} memberId={}", companyId, memberId, e);
            return null;
        }
    }

    private String describeMode(RetirementType t) {
        return switch (t) {
            case LEGAL -> "법정 퇴직금 사내 적립 일시금 지급";
            case DB -> "DB형 퇴직연금 외부 금융기관 운영 계산식 동일";
            case DC -> "DC형 퇴직연금 매월 1 12 외부 적립 운용수익 별도";
        };
    }

    private String disclaimer(RetirementType t) {
        return switch (t) {
            case LEGAL, DB ->
                    "평균임금은 근로기준법 제2조 1항 6호 기준으로 직전 3개월 임금총액에 12개월 상여 / 연차수당 환산을 가산해 산정합니다. 시행령 제2조에 따라 출산휴가 / 육아휴직 / 산재요양 / 병역 등 휴직기간은 분모 일수에서 제외하며, 평균임금이 통상시급 일액보다 적으면 통상시급 일액이 적용됩니다.";
            case DC ->
                    "본 추정치는 평균월급 기준 근사치이며 정확한 적립금은 가입 금융기관 화면에서 확인하세요.";
        };
    }

    /** 내부 평균임금 산정 결과 캐리어 */
    private static class AverageWage {
        long basePeriodPayment;
        int basePeriodDays;
        long simpleDailyAverage;
        // 시행령 제2조 제외 기간
        int excludedLeaveDays;
        int excludedLeaveCount;
        int adjustedPeriodDays;
        long bonusAddition12mAvg;
        long unusedLeaveAddition12mAvg;
        long averageDailyWage;
        long ordinaryDailyWage;
        long appliedDailyWage;
        String appliedBasis = "AVERAGE";
    }
}
