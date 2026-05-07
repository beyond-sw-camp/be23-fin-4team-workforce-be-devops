package com._team._team.batch.payroll.worker;

import com._team._team.dto.BusinessException;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.repository.SalaryRepository;
import com._team._team.salary.service.PayrollCalculationService;
import com._team._team.salary.service.PayrollService;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.PayrollType;
import com._team._team.salary.dto.reqdto.PayrollCreateReqDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// 월 급여 일괄 산정 배치 워커
@Slf4j
@Component
public class PayrollCalculateWorker {

    private final PayrollService payrollService;
    private final PayrollCalculationService payrollCalculationService;
    private final MemberFeignClient memberFeignClient;
    private final SalaryRepository salaryRepository;

    @Autowired
    public PayrollCalculateWorker(
            PayrollService payrollService,
            PayrollCalculationService payrollCalculationService,
            MemberFeignClient memberFeignClient,
            SalaryRepository salaryRepository) {
        this.payrollService = payrollService;
        this.payrollCalculationService = payrollCalculationService;
        this.memberFeignClient = memberFeignClient;
        this.salaryRepository = salaryRepository;
    }

    // 전체 회사 일괄 급여 계산 - Quartz 가 매월 말일 호출
    public void run() {
        Instant startedAt = Instant.now();
        LocalDate today = LocalDate.now();
        log.info("[PAYROLL-BATCH] 시작 today={}", today);

        List<UUID> companyIds = salaryRepository.findDistinctCompanyIds();
        if (companyIds.isEmpty()) {
            log.info("[PAYROLL-BATCH] Salary 있는 회사 없음 -> 종료");
            return;
        }

        Counter total = new Counter();
        for (UUID companyId : companyIds) {
            Counter c = processCompany(companyId, today, null);
            total.add(c);
        }

        long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
        log.info("[PAYROLL-BATCH] 종료 today={} 회사수={} ok={} 중복스킵={} 급여없음={} 기타={} 실패={} 소요={}ms",
                today, companyIds.size(),
                total.ok, total.dup, total.noSalary, total.badReq, total.fail, elapsedMs);
    }

    // 셀프서비스 - 회사 단위 재계산
    // forcedSettlement 명시하면 그 날짜로, 안 주면 정책 기준 자동
    public Counter runForCompany(UUID companyId, LocalDate forcedSettlement) {
        Instant startedAt = Instant.now();
        LocalDate today = LocalDate.now();
        log.info("[PAYROLL-RECALC] 시작 companyId={} forcedSettlement={}", companyId, forcedSettlement);

        Counter c = processCompany(companyId, today, forcedSettlement);

        long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
        log.info("[PAYROLL-RECALC] 종료 companyId={} ok={} 중복스킵={} 급여없음={} 기타={} 실패={} 소요={}ms",
                companyId, c.ok, c.dup, c.noSalary, c.badReq, c.fail, elapsedMs);
        return c;
    }

    // 회사 1곳 처리 - run() / runForCompany() 공통 로직
    private Counter processCompany(UUID companyId, LocalDate today, LocalDate forcedSettlement) {
        Counter c = new Counter();

        SalaryPolicy policy = payrollCalculationService.findActivePolicy(companyId, today);
        if (policy == null) {
            log.warn("[PAYROLL] companyId={} 활성 SalaryPolicy 없음 건너뜀", companyId);
            return c;
        }
        LocalDate settlement = (forcedSettlement != null)
                ? forcedSettlement
                : resolveSettlementDate(today, policy.getPayDay());

        List<MemberResDto> members = fetchMembers(companyId);

        for (MemberResDto member : members) {
            try {
                payrollService.createPayroll(
                        companyId,
                        PayrollCreateReqDto.builder()
                                .memberId(member.getMemberId())
                                .payrollYearMonthDay(settlement)
                                .payrollType(PayrollType.REGULAR_MONTHLY)
                                .build());
                c.ok++;
            } catch (BusinessException e) {
                HttpStatus status = e.getStatus();
                if (status == HttpStatus.CONFLICT) {
                    c.dup++;
                } else if (status == HttpStatus.NOT_FOUND) {
                    c.noSalary++;
                    log.warn("[PAYROLL] 활성 Salary 없음 companyId={} memberId={} settlement={}",
                            companyId, member.getMemberId(), settlement);
                } else {
                    c.badReq++;
                    log.warn("[PAYROLL] BusinessException companyId={} memberId={} settlement={} status={} msg={}",
                            companyId, member.getMemberId(), settlement, status, e.getMessage());
                }
            } catch (Exception e) {
                c.fail++;
                log.error("[PAYROLL] 실패 companyId={} memberId={} settlement={} type={} msg={}",
                        companyId, member.getMemberId(), settlement,
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }

        log.info("[PAYROLL] 회사 요약 companyId={} settlement={} 대상={} ok={} 중복스킵={} 급여없음={} 기타={} 실패={}",
                companyId, settlement, members.size(), c.ok, c.dup, c.noSalary, c.badReq, c.fail);
        return c;
    }

    // 오늘 이후 가장 가까운 미래 payDay 반환
    // 말일 배치 기준: 이번 달 payDay가 미래면 이번 달, 아니면 다음 달 payDay
    private static LocalDate resolveSettlementDate(LocalDate today, int payDay) {
        int dayThisMonth = Math.min(payDay, today.lengthOfMonth());
        LocalDate payThisMonth = today.withDayOfMonth(dayThisMonth);

        if (today.isBefore(payThisMonth)) {
            return payThisMonth;
        }

        LocalDate nextMonth = today.plusMonths(1);
        int dayNextMonth = Math.min(payDay, nextMonth.lengthOfMonth());
        return nextMonth.withDayOfMonth(dayNextMonth);
    }

    private List<MemberResDto> fetchMembers(UUID companyId) {
        var res = memberFeignClient.getMembersByCompany(companyId);
        if (res == null || res.getData() == null) {
            return Collections.emptyList();
        }
        return res.getData();
    }

    // 처리 결과 카운터 - 회사간 합산 / API 응답에 같이 씀
    public static class Counter {
        public int ok = 0;
        public int dup = 0;
        public int noSalary = 0;
        public int badReq = 0;
        public int fail = 0;

        public void add(Counter other) {
            this.ok += other.ok;
            this.dup += other.dup;
            this.noSalary += other.noSalary;
            this.badReq += other.badReq;
            this.fail += other.fail;
        }
    }
}
