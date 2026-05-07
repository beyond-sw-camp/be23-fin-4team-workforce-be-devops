package com._team._team.salary.service;


import com._team._team.attendance.repository.LeavePolicyRepository;
import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.dto.BusinessException;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.repository.SalaryRepository;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.dto.reqdto.UnusedLeavePayoutApplyReqDto;
import com._team._team.salary.dto.resdto.UnusedLeavePayoutPreviewResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <미사용 연차수당 수동 처리 서비스>
 * - 운영 플로우 -
 * 1. HR 관리자가 연도 (baseYear = 지급할 대상 연도, 즉 전년도) 지정하여 미리보기 호출
 * 2. 담당자가 금액 검토·조정 → apply 호출 -> 1월 Payroll(DRAFT) 에 PayrollItem 실제 추가
 * - 계산 수식 -
 * 통상시급 == prevDecSalary.baseSalary / 209
 * 1일 통상임금 == 통상시급 × 8
 * 수당 기본값 == 1일 통상임금 × 미이월잔여일수
 * - 신규 ANNUAL 제외 -
 * 쿼리 조건에 `expirationDate BETWEEN 전년1/1 ~ 전년12/31` 명시
 * -> 1/1 에 새로 생긴 ANNUAL (expirationDate : 당해12/31) 은 자동 제외
 */
@Slf4j
@Service
@Transactional
public class UnusedLeavePayoutService {

    private static final double MONTHLY_WORK_HOURS = 209.0;
    private static final double DAILY_WORK_HOURS = 8.0;
    private static final String PAYOUT_ITEM_NAME = "연차미사용수당";

    private final LeavePolicyRepository leavePolicyRepository;
    private final MemberBalanceRepository memberBalanceRepository;
    private final SalaryRepository salaryRepository;
    private final PayrollRepository payrollRepository;
    private final SalaryItemTemplateRepository salaryItemTemplateRepository;

    @Autowired
    public UnusedLeavePayoutService(LeavePolicyRepository leavePolicyRepository, MemberBalanceRepository memberBalanceRepository, SalaryRepository salaryRepository, PayrollRepository payrollRepository, SalaryItemTemplateRepository salaryItemTemplateRepository) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.memberBalanceRepository = memberBalanceRepository;
        this.salaryRepository = salaryRepository;
        this.payrollRepository = payrollRepository;
        this.salaryItemTemplateRepository = salaryItemTemplateRepository;
    }

    /**
     * 미사용 수당 미리보기
     */
    @Transactional(readOnly = true)
    public List<UnusedLeavePayoutPreviewResDto> preview(UUID companyId, int baseYear, YearMonth targetPayrollMonth) {

        /** 1. 정책 확인 */
        LeavePolicy policy = leavePolicyRepository.findByCompanyIdAndDelYn(companyId, "N")
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "휴가 정책이 존재하지 않습니다."));

        if (!"Y".equals(policy.getIsPayoutYn())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "미사용 연차 수당 지급 정책이 비활성화되어 있습니다.");
        }

        /** 2. 템플릿 확인 — 회사가 '연차미사용수당' 항목을 미리 등록해두어야 함 */
        salaryItemTemplateRepository.findByCompanyIdAndDelYn(companyId, "N").stream()
                .filter(t -> PAYOUT_ITEM_NAME.equals(t.getItemName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "'" + PAYOUT_ITEM_NAME + "' 급여 항목 템플릿을 먼저 등록해주세요."));

        LocalDate prevYearStart = LocalDate.of(baseYear, 1, 1);
        LocalDate prevYearEnd   = LocalDate.of(baseYear, 12, 31);
        LocalDate newYearStart  = LocalDate.of(baseYear + 1, 1, 1);
        LocalDate newYearEnd    = LocalDate.of(baseYear + 1, 12, 31);
        LocalDate prevDec31     = prevYearEnd;

        LocalDate targetMonthStart = targetPayrollMonth.atDay(1);
        LocalDate targetMonthEnd   = targetPayrollMonth.atEndOfMonth();

        /** 3. 전년도 ANNUAL 조회 (만료 여부 무관) */
        List<MemberBalance> prevAnnuals = memberBalanceRepository
                .findBalancesByCompanyAndTypeAndExpirationIncludingExpired(
                        companyId, BalanceType.ANNUAL, prevYearStart, prevYearEnd);

        List<UnusedLeavePayoutPreviewResDto> result = new ArrayList<>();

        for (MemberBalance prev : prevAnnuals) {
            UUID memberId = prev.getMemberId();
            double remaining = prev.getRemaining() == null ? 0 : prev.getRemaining();

            /** 이월 차감 */
            double carriedDays = memberBalanceRepository
                    .findBalanceByMemberAndTypeAndExpiration(
                            companyId, memberId, BalanceType.CARRYOVER, newYearStart, newYearEnd)
                    .map(mb -> mb.getTotalGranted() == null ? 0.0 : mb.getTotalGranted())
                    .orElse(0.0);

            double payoutDays = remaining - carriedDays;
            if (payoutDays <= 0) continue;

            /** 전년 12월 기준 Salary (법대로) */
            Salary prevDecSalary = salaryRepository.findActiveSalary(memberId, companyId, prevDec31).orElse(null);
            if (prevDecSalary == null) {
                result.add(UnusedLeavePayoutPreviewResDto.builder()
                        .memberId(memberId)
                        .unusedDays(payoutDays)
                        .hasSalary(false)
                        .warning("전년 12월 유효 급여 정보 없음 — 수당 계산 불가")
                        .build());
                continue;
            }

            long baseSalary = prevDecSalary.getBaseSalary();
            double hourlyWage = baseSalary / MONTHLY_WORK_HOURS;
            double dailyWage = hourlyWage * DAILY_WORK_HOURS;
            long payoutAmount = Math.round(dailyWage * payoutDays);

            /** 대상 Payroll 조회 (지정한 연월의 DRAFT) */
            Payroll targetPayroll = payrollRepository
                    .findDraftByCompanyMemberAndMonth(
                            companyId, memberId, targetMonthStart, targetMonthEnd, PayrollStatus.DRAFT)
                    .orElse(null);

            String warning = null;
            boolean alreadyApplied = false;
            if (targetPayroll == null) {
                warning = targetPayrollMonth + " 급여대장(DRAFT) 미존재 — 생성 후 재조회해주세요.";
            } else {
                alreadyApplied = targetPayroll.getPayrollItemList().stream()
                        .anyMatch(it -> PAYOUT_ITEM_NAME.equals(it.getItemName())
                                && "N".equals(it.getDelYn()));
                if (alreadyApplied) {
                    warning = "이미 수당 항목이 반영되어 있어 재실행 시 중복될 수 있습니다.";
                }
            }

            result.add(UnusedLeavePayoutPreviewResDto.builder()
                    .memberId(memberId)
                    .baseSalary(baseSalary)
                    .dailyWage(Math.round(dailyWage))
                    .unusedDays(payoutDays)
                    .calculatedAmount(payoutAmount)
                    .targetPayrollId(targetPayroll == null ? null : targetPayroll.getPayrollId())
                    .hasSalary(true)
                    .alreadyApplied(alreadyApplied)
                    .warning(warning)
                    .build());
        }

        return result;
    }

    /**
     * 미사용 수당 1월 Payroll 반영 (담당자 확정 호출)
     * 1월 Payroll 이 DRAFT 상태일 때만 허용
     */
    public void apply(UUID companyId, UnusedLeavePayoutApplyReqDto reqDto) {

        SalaryItemTemplate template = salaryItemTemplateRepository.findByCompanyIdAndDelYn(companyId, "N").stream()
                .filter(t -> PAYOUT_ITEM_NAME.equals(t.getItemName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "'" + PAYOUT_ITEM_NAME + "' 급여 항목 템플릿을 먼저 등록해주세요."));

        for (UnusedLeavePayoutApplyReqDto.Item item : reqDto.getItems()) {
            Payroll payroll = payrollRepository.findByPayrollIdAndCompanyIdAndDelYn(
                            item.getPayrollId(), companyId, "N")
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "급여대장을 찾을 수 없습니다. payrollId=" + item.getPayrollId()));

            if (!payroll.isModifiable()) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "DRAFT 상태의 급여대장만 수정 가능합니다. payrollId=" + payroll.getPayrollId());
            }

            /** 중복 방지 */
            boolean exists = payroll.getPayrollItemList().stream()
                    .anyMatch(it -> PAYOUT_ITEM_NAME.equals(it.getItemName())
                            && "N".equals(it.getDelYn()));
            if (exists) {
                log.warn("Skip duplicate payout item. payrollId={}", payroll.getPayrollId());
                continue;
            }

            /** 템플릿 스냅샷 기반 생성 */
            PayrollItem newItem = PayrollItem.fromTemplate(payroll, template, item.getAmount());
            payroll.getPayrollItemList().add(newItem);

            long totalPayment = payroll.getTotalPayment() + item.getAmount();
            payroll.recalculate(totalPayment, payroll.getTotalDeduction());

            log.info("Applied payout payrollId={}, memberId={}, amount={}",
                    payroll.getPayrollId(), payroll.getMemberId(), item.getAmount());
        }
    }
}