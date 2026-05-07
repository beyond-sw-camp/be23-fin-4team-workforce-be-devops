package com._team._team.salary.service;

import com._team._team.attendance.domain.MonthlyAttendanceLedger;
import com._team._team.attendance.repository.MonthlyAttendanceLedgerRepository;
import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.domain.enums.PayrollType;
import com._team._team.salary.domain.vo.AutoPayrollItem;
import com._team._team.salary.dto.reqdto.RetroactivePayrollReqDto;
import com._team._team.salary.dto.resdto.RetroactivePayrollResDto;
import com._team._team.salary.repository.PayrollItemRepository;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 소급분 자동 재계산 서비스
 *  통상임금 인상 (단협 / 호봉승급 / 기본급 인상) 시 과거 월 가산수당을 새 통상임금 기준으로 재계산
 *  미리보기 (preview) → 차액 표시 후 관리자가 발행 (apply) → RETROACTIVE 타입 Payroll 1건 신규 생성
 */
@Slf4j
@Service
@Transactional
public class RetroactivePayrollService {

    private static final Set<String> ALLOWANCE_NAMES = Set.of(
            "초과근무수당", "공휴일 근무수당", "야간근무수당"
    );

    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final SalaryPolicyRepository salaryPolicyRepository;
    private final SalaryRepository salaryRepository;
    private final MonthlyAttendanceLedgerRepository monthlyAttendanceLedgerRepository;
    private final PayrollCalculationService payrollCalculationService;

    @Autowired
    public RetroactivePayrollService(PayrollRepository payrollRepository,
                                     PayrollItemRepository payrollItemRepository,
                                     SalaryPolicyRepository salaryPolicyRepository,
                                     SalaryRepository salaryRepository,
                                     MonthlyAttendanceLedgerRepository monthlyAttendanceLedgerRepository,
                                     PayrollCalculationService payrollCalculationService) {
        this.payrollRepository = payrollRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.salaryPolicyRepository = salaryPolicyRepository;
        this.salaryRepository = salaryRepository;
        this.monthlyAttendanceLedgerRepository = monthlyAttendanceLedgerRepository;
        this.payrollCalculationService = payrollCalculationService;
    }

    /**
     * 미리보기 차액만 계산 DB 변경 없음
     */
    @Transactional(readOnly = true)
    public RetroactivePayrollResDto preview(UUID companyId, RetroactivePayrollReqDto reqDto) {
        validatePeriod(reqDto.getFromMonth(), reqDto.getToMonth());

        List<RetroactivePayrollResDto.MonthlyDiff> diffs = computeMonthlyDiffs(
                companyId, reqDto.getMemberId(),
                reqDto.getFromMonth(), reqDto.getToMonth(),
                reqDto.getNewOrdinaryWage());

        long totalDiff = diffs.stream().mapToLong(RetroactivePayrollResDto.MonthlyDiff::getDiff).sum();

        return RetroactivePayrollResDto.builder()
                .memberId(reqDto.getMemberId())
                .fromMonth(reqDto.getFromMonth())
                .toMonth(reqDto.getToMonth())
                .previousOrdinaryWage(estimatePreviousOrdinary(diffs, reqDto.getNewOrdinaryWage()))
                .newOrdinaryWage(reqDto.getNewOrdinaryWage())
                .monthlyDiffs(diffs)
                .totalDiff(totalDiff)
                .message(totalDiff > 0
                        ? "소급분 발행 가능 합계 " + totalDiff + "원"
                        : (totalDiff == 0
                                ? "차액 없음 — 발행 불필요"
                                : "차액 음수 — 환수 시나리오는 별도 처리"))
                .build();
    }

    /**
     * 발행 차액을 RETROACTIVE 타입 Payroll 1행으로 신규 생성
     *  단일 PayrollItem "소급분 차액 (YYYY-MM ~ YYYY-MM)" 으로 합계 표기
     *  PAID 가 아니라 DRAFT 로 발행 — 인사팀이 검토 후 확정
     */
    public RetroactivePayrollResDto apply(UUID companyId, RetroactivePayrollReqDto reqDto) {
        RetroactivePayrollResDto preview = preview(companyId, reqDto);

        if (preview.getTotalDiff() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "차액이 0 또는 음수입니다 — 소급분 발행 대상이 아닙니다.");
        }

        // 활성 Salary 참조 salaryId 채우기 위해
        UUID salaryId = salaryRepository
                .findActiveSalary(reqDto.getMemberId(), companyId, LocalDate.now())
                .map(s -> s.getSalaryId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "활성 급여 정보가 없습니다 — 소급분 발행 불가."));

        LocalDate today = LocalDate.now();

        // 동일 직원 동일 일자 중복 회피 (Unique key)
        // 소급분은 같은 날 여러 번 발행 가능해야 하므로 +1day 시도 — 단순화 위해 충돌 시 예외
        if (payrollRepository
                .findByCompanyIdAndMemberIdAndPayrollYearMonthDay(companyId, reqDto.getMemberId(), today)
                .isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "오늘 날짜로 이미 발행된 급여대장이 있습니다 — 다른 일자로 재시도하세요.");
        }

        long totalDiff = preview.getTotalDiff();

        Payroll retroactive = Payroll.builder()
                .companyId(companyId)
                .salaryId(salaryId)
                .memberId(reqDto.getMemberId())
                .payrollYearMonthDay(today)
                // 소급분 발행일이 속한 월을 targetYearMonth 로 기록
                .targetYearMonth(java.time.YearMonth.from(today).toString())
                .totalPayment(totalDiff)
                .totalDeduction(0L)   // 소급분은 합계만 표기 4대보험 / 원천세는 다음 정기급여에 합산 시 자동 계산
                .netPay(totalDiff)
                .payrollStatus(PayrollStatus.DRAFT)
                .payrollType(PayrollType.RETROACTIVE)
                .build();

        Payroll saved = payrollRepository.save(retroactive);

        // 단일 항목으로 차액 합계 표기
        String label = String.format("소급분 차액 (%s ~ %s)",
                reqDto.getFromMonth(), reqDto.getToMonth());
        PayrollItem diffItem = PayrollItem.builder()
                .payroll(saved)
                .itemName(label)
                .itemType(ItemType.EARNING)
                .amount(totalDiff)
                .displayOrder(10)
                .isTaxableYn("Y")
                .build();
        payrollItemRepository.save(diffItem);

        log.info("[RETROACTIVE] 발행 companyId={} memberId={} from={} to={} diff={} payrollId={}",
                companyId, reqDto.getMemberId(),
                reqDto.getFromMonth(), reqDto.getToMonth(), totalDiff, saved.getPayrollId());

        return RetroactivePayrollResDto.builder()
                .memberId(preview.getMemberId())
                .fromMonth(preview.getFromMonth())
                .toMonth(preview.getToMonth())
                .previousOrdinaryWage(preview.getPreviousOrdinaryWage())
                .newOrdinaryWage(preview.getNewOrdinaryWage())
                .monthlyDiffs(preview.getMonthlyDiffs())
                .totalDiff(totalDiff)
                .newPayrollId(saved.getPayrollId())
                .issuedDate(today.toString())
                .message("소급분 명세서가 DRAFT 상태로 발행되었습니다 — 검토 후 확정 / 지급 처리하세요.")
                .build();
    }

    /* ─────────────── 내부 ─────────────── */

    private void validatePeriod(YearMonth from, YearMonth to) {
        if (from == null || to == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "소급 기간이 비어 있습니다.");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "소급 시작월이 종료월보다 빠릅니다.");
        }
        YearMonth thisMonth = YearMonth.now();
        if (to.isAfter(thisMonth)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "소급 종료월이 현재 월보다 미래일 수 없습니다.");
        }
    }

    /**
     * 월별 차액 계산
     *  fromMonth ~ toMonth 까지 PAID Payroll 만 대상
     *  새 통상임금 + 활성 SalaryPolicy 로 가산수당 재계산
     */
    private List<RetroactivePayrollResDto.MonthlyDiff> computeMonthlyDiffs(
            UUID companyId, UUID memberId,
            YearMonth fromMonth, YearMonth toMonth,
            long newOrdinaryWage) {

        SalaryPolicy activePolicy = salaryPolicyRepository
                .findActivePolicies(companyId, LocalDate.now())
                .stream().findFirst().orElse(null);

        List<RetroactivePayrollResDto.MonthlyDiff> result = new ArrayList<>();
        YearMonth cursor = fromMonth;
        while (!cursor.isAfter(toMonth)) {
            LocalDate monthStart = cursor.atDay(1);
            LocalDate monthEnd = cursor.atEndOfMonth();
            // 해당 월 PAID 정기급여 단건 조회
            Optional<Payroll> payrollOpt = payrollRepository.findInYearByMemberFetchItems(
                            companyId, memberId, monthStart, monthEnd)
                    .stream()
                    .filter(p -> p.getPayrollType() == null || p.getPayrollType() == PayrollType.REGULAR_MONTHLY)
                    .filter(p -> p.getPayrollStatus() == PayrollStatus.PAID)
                    .findFirst();

            if (payrollOpt.isEmpty()) {
                cursor = cursor.plusMonths(1);
                continue;
            }
            Payroll payroll = payrollOpt.get();

            // 기존 가산수당 합계 (item name 매칭)
            long oldAllowance = payroll.getPayrollItemList() == null
                    ? 0L
                    : payroll.getPayrollItemList().stream()
                        .filter(i -> i.getItemType() == ItemType.EARNING)
                        .filter(i -> "N".equals(i.getDelYn()))
                        .filter(i -> ALLOWANCE_NAMES.contains(i.getItemName()))
                        .mapToLong(i -> i.getAmount() == null ? 0L : i.getAmount())
                        .sum();

            // 새 통상임금 기준 가산수당 재계산
            long newAllowance = recomputeAllowanceForMonth(
                    companyId, memberId, cursor, newOrdinaryWage, activePolicy);

            long diff = newAllowance - oldAllowance;

            result.add(RetroactivePayrollResDto.MonthlyDiff.builder()
                    .month(cursor)
                    .oldAllowance(oldAllowance)
                    .newAllowance(newAllowance)
                    .diff(diff)
                    .sourcePayrollId(payroll.getPayrollId())
                    .build());

            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    /**
     * 특정 월 가산수당 재계산
     *  Ledger 가 있어야 가산수당 산정 가능 (overtimeMinutes / nightMinutes / holidayMinutes)
     *  Ledger 없으면 0 반환 (실적 0이라 차액 0)
     */
    private long recomputeAllowanceForMonth(UUID companyId, UUID memberId,
                                            YearMonth month, long newOrdinaryWage,
                                            SalaryPolicy policy) {
        return monthlyAttendanceLedgerRepository
                .findByMemberIdAndLedgerYearMonth(memberId, month.toString())
                .map(ledger -> {
                    long sum = 0L;
                    AutoPayrollItem ot = payrollCalculationService.calculateOvertimePay(ledger, newOrdinaryWage, policy);
                    if (ot != null) sum += ot.amount();
                    AutoPayrollItem holiday = payrollCalculationService.calculateHolidayWorkPay(ledger, newOrdinaryWage, policy);
                    if (holiday != null) sum += holiday.amount();
                    AutoPayrollItem night = payrollCalculationService.calculateNightWorkPay(ledger, newOrdinaryWage, policy);
                    if (night != null) sum += night.amount();
                    return sum;
                })
                .orElse(0L);
    }

    /**
     * 차액 + 새 통상임금 으로부터 기존 통상임금 역산 (참고용)
     *  새 통상임금 / 새 가산수당 = 비율 → 기존 가산수당 / 비율 = 기존 통상임금 추정
     *  데이터 부족 시 newOrdinaryWage 그대로 반환
     */
    private long estimatePreviousOrdinary(List<RetroactivePayrollResDto.MonthlyDiff> diffs,
                                          long newOrdinaryWage) {
        long newTotal = diffs.stream().mapToLong(RetroactivePayrollResDto.MonthlyDiff::getNewAllowance).sum();
        long oldTotal = diffs.stream().mapToLong(RetroactivePayrollResDto.MonthlyDiff::getOldAllowance).sum();
        if (newTotal <= 0 || oldTotal <= 0) return newOrdinaryWage;
        return newOrdinaryWage * oldTotal / newTotal;
    }
}
