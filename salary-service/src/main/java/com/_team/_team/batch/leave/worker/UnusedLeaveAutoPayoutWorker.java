package com._team._team.batch.leave.worker;

import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.repository.LeavePolicyRepository;
import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 미사용 연차 수당 자동 정산 배치 워커
 * isCarryoverYn='N' + isPayoutYn='Y' 일 경우만, 정책 보다 보면 재밋네 아주
 */
@Slf4j
@Component
public class UnusedLeaveAutoPayoutWorker {

    private static final double MONTHLY_WORK_HOURS = 209.0;
    private static final double DAILY_WORK_HOURS = 8.0;
    private static final String PAYOUT_ITEM_NAME = "연차미사용수당";
    private static final int BATCH_SIZE = 200;

    private final MemberBalanceRepository memberBalanceRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final SalaryRepository salaryRepository;
    private final PayrollRepository payrollRepository;
    private final SalaryItemTemplateRepository salaryItemTemplateRepository;

    @Autowired
    public UnusedLeaveAutoPayoutWorker(MemberBalanceRepository memberBalanceRepository,
                                       LeavePolicyRepository leavePolicyRepository,
                                       SalaryRepository salaryRepository,
                                       PayrollRepository payrollRepository,
                                       SalaryItemTemplateRepository salaryItemTemplateRepository) {
        this.memberBalanceRepository = memberBalanceRepository;
        this.leavePolicyRepository = leavePolicyRepository;
        this.salaryRepository = salaryRepository;
        this.payrollRepository = payrollRepository;
        this.salaryItemTemplateRepository = salaryItemTemplateRepository;
    }

    public Result run(LocalDate today) {
        log.info("[UnusedLeaveAutoPayout] start date={}", today);

        // 1) 오늘 만료될 ANNUAL 잔고 + remaining > 0 일괄 조회
        List<MemberBalance> targets = memberBalanceRepository.findExpiringAnnualWithRemaining(today);
        if (targets.isEmpty()) {
            log.info("[UnusedLeaveAutoPayout] no targets date={}", today);
            return Result.empty();
        }

        // 2) 회사별 그루핑
        Map<UUID, List<MemberBalance>> byCompany = targets.stream()
                .collect(Collectors.groupingBy(MemberBalance::getCompanyId));

        Result total = new Result();
        total.companies = byCompany.size();

        for (Map.Entry<UUID, List<MemberBalance>> e : byCompany.entrySet()) {
            UUID companyId = e.getKey();
            try {
                Result r = processCompany(companyId, e.getValue(), today);
                total.applied += r.applied;
                total.skip += r.skip;
                total.fail += r.fail;
            } catch (Exception ex) {
                // 회사 단위 트랜잭션이 통째 실패해도 다른 회사 처리는 계속
                log.error("[UnusedLeaveAutoPayout] company-level failure companyId={}", companyId, ex);
                total.fail += e.getValue().size();
            }
        }

        log.info("[UnusedLeaveAutoPayout] done date={} companies={} applied={} skip={} fail={}",
                today, total.companies, total.applied, total.skip, total.fail);
        return total;
    }

    /**
     * 회사 단위 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result processCompany(UUID companyId, List<MemberBalance> balances, LocalDate today) {
        Result r = new Result();

        // 3) 정책 1번 조회. 자동 정산 대상 조건 , (N+Y) 이 아니면 전체 스킵
        LeavePolicy policy = leavePolicyRepository.findByCompanyIdAndDelYn(companyId, "N")
                .stream().findFirst().orElse(null);
        if (policy == null
                || !"N".equals(policy.getIsCarryoverYn())
                || !"Y".equals(policy.getIsPayoutYn())) {
            r.skip = balances.size();
            log.debug("[UnusedLeaveAutoPayout] policy not eligible companyId={} skip={}",
                    companyId, balances.size());
            return r;
        }

        // 4) 템플릿 1번 조회. 없으면 전체 스킵 (회사가 미리 등록해야 동작)
        SalaryItemTemplate template = salaryItemTemplateRepository.findByCompanyIdAndDelYn(companyId, "N")
                .stream()
                .filter(t -> PAYOUT_ITEM_NAME.equals(t.getItemName()))
                .findFirst()
                .orElse(null);
        if (template == null) {
            r.skip = balances.size();
            log.warn("[UnusedLeaveAutoPayout] missing item template companyId={} skip={}",
                    companyId, balances.size());
            return r;
        }

        // 5) Bulk fetch — 활성 Salary 1쿼리, DRAFT Payroll 1쿼리(JOIN FETCH PayrollItem)
        Set<UUID> memberIds = balances.stream()
                .map(MemberBalance::getMemberId)
                .collect(Collectors.toSet());

        Map<UUID, Salary> salaryByMember = salaryRepository
                .findActiveSalariesByMemberIds(companyId, memberIds, today)
                .stream()
                .collect(Collectors.toMap(Salary::getMemberId, s -> s, (a, b) -> a));

        // 정산 대상 월 = 만료일 다음 달의 DRAFT Payroll
        YearMonth targetMonth = YearMonth.from(today.plusMonths(1));
        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEnd = targetMonth.atEndOfMonth();

        Map<UUID, Payroll> payrollByMember = payrollRepository
                .findDraftsByCompanyMemberIdsAndMonthFetchItems(
                        companyId, memberIds, monthStart, monthEnd, PayrollStatus.DRAFT)
                .stream()
                .collect(Collectors.toMap(Payroll::getMemberId, p -> p, (a, b) -> a));

        // 6) 각 직원 처리 — buffer 에 모아 BATCH_SIZE 마다 flush
        List<Payroll> dirtyBuffer = new ArrayList<>(BATCH_SIZE);

        for (MemberBalance bal : balances) {
            try {
                applyOne(bal, policy, template, salaryByMember, payrollByMember,
                        targetMonth, dirtyBuffer, r);
            } catch (Exception ex) {
                r.fail++;
                log.warn("[UnusedLeaveAutoPayout] failed memberBalanceId={} memberId={}",
                        bal.getMemberBalanceId(), bal.getMemberId(), ex);
            }

            if (dirtyBuffer.size() >= BATCH_SIZE) {
                payrollRepository.saveAll(dirtyBuffer);
                payrollRepository.flush();
                dirtyBuffer.clear();
            }
        }

        if (!dirtyBuffer.isEmpty()) {
            payrollRepository.saveAll(dirtyBuffer);
            payrollRepository.flush();
        }

        log.info("[UnusedLeaveAutoPayout] company done companyId={} applied={} skip={} fail={}",
                companyId, r.applied, r.skip, r.fail);
        return r;
    }

    /** 직원 1명 처리 멱등성 체크, 수당 계산, PayrollItem 추가 후 buffer 적재. */
    private void applyOne(MemberBalance bal,
                          LeavePolicy policy,
                          SalaryItemTemplate template,
                          Map<UUID, Salary> salaryByMember,
                          Map<UUID, Payroll> payrollByMember,
                          YearMonth targetMonth,
                          List<Payroll> dirtyBuffer,
                          Result r) {
        UUID memberId = bal.getMemberId();
        double payoutDays = bal.getRemaining() == null ? 0.0 : bal.getRemaining();
        if (payoutDays <= 0) {
            r.skip++;
            return;
        }

        Salary salary = salaryByMember.get(memberId);
        if (salary == null || salary.getBaseSalary() == null) {
            log.warn("[UnusedLeaveAutoPayout] active salary missing memberId={}", memberId);
            r.skip++;
            return;
        }

        Payroll payroll = payrollByMember.get(memberId);
        if (payroll == null) {
            log.warn("[UnusedLeaveAutoPayout] draft payroll missing memberId={} month={}",
                    memberId, targetMonth);
            r.skip++;
            return;
        }
        if (!payroll.isModifiable()) {
            log.warn("[UnusedLeaveAutoPayout] payroll not modifiable memberId={} payrollId={}",
                    memberId, payroll.getPayrollId());
            r.skip++;
            return;
        }

        // 멱등성: 같은 월에 이미 수당 항목이 있으면 스킵 (수동 적용 후 재실행 등)
        boolean alreadyApplied = payroll.getPayrollItemList().stream()
                .anyMatch(it -> PAYOUT_ITEM_NAME.equals(it.getItemName())
                        && "N".equals(it.getDelYn()));
        if (alreadyApplied) {
            r.skip++;
            return;
        }

        // 수당 계산: 통상시급(기본급/209) × 8h × 미사용일수
        long baseSalary = salary.getBaseSalary();
        double dailyWage = (baseSalary / MONTHLY_WORK_HOURS) * DAILY_WORK_HOURS;
        long amount = Math.round(dailyWage * payoutDays);

        PayrollItem newItem = PayrollItem.fromTemplate(payroll, template, amount);
        payroll.getPayrollItemList().add(newItem);
        long newTotalPayment = (payroll.getTotalPayment() == null ? 0L : payroll.getTotalPayment()) + amount;
        payroll.recalculate(newTotalPayment, payroll.getTotalDeduction());

        dirtyBuffer.add(payroll);
        r.applied++;

        log.info("[UnusedLeaveAutoPayout] applied memberId={} days={} amount={} payrollId={}",
                memberId, payoutDays, amount, payroll.getPayrollId());
    }

    /** 처리 결과 카운터 */
    public static class Result {
        public int companies = 0;
        public int applied = 0;
        public int skip = 0;
        public int fail = 0;

        public static Result empty() {
            return new Result();
        }

        @Override
        public String toString() {
            return "applied=" + applied + " skip=" + skip + " fail=" + fail
                    + " companies=" + companies;
        }
    }
}
