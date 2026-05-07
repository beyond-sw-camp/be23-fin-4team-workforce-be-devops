package com._team._team.batch.leave.worker;

import com._team._team.attendance.repository.LeavePolicyRepository;
import com._team._team.attendance.service.AnnualLeaveGrantService;
import com._team._team.attendance.domain.LeavePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

// 휴가 부여 배치 진입점
// 1. 입사기념일 연차 2. 회계연도 연차 3. 월차
@Slf4j
@Component
public class LeaveGrantWorker {

    private final AnnualLeaveGrantService annualLeaveGrantService;
    private final MonthlyLeaveGrantWorker monthlyLeaveGrantWorker;
    private final LeavePolicyRepository leavePolicyRepository;

    @Autowired
    public LeaveGrantWorker(AnnualLeaveGrantService annualLeaveGrantService, MonthlyLeaveGrantWorker monthlyLeaveGrantWorker, LeavePolicyRepository leavePolicyRepository) {
        this.annualLeaveGrantService = annualLeaveGrantService;
        this.monthlyLeaveGrantWorker = monthlyLeaveGrantWorker;
        this.leavePolicyRepository = leavePolicyRepository;
    }

    /**
     * baseDate를 기준으로 연차·월차 부여 전체 플로우를 실행하고 로그에 집계 결과를 남김
     */
    public void run(LocalDate baseDate) {
        log.info("LeaveGrantWorker start baseDate={}", baseDate);

        AnnualLeaveGrantService.BatchResult hire = annualLeaveGrantService.grantHireDate(baseDate);
        log.info("Leave grant HIRE_DATE: success={}, skip={}, errors={}", hire.successCount, hire.skipCount, hire.errorCount);

        AnnualLeaveGrantService.BatchResult fiscal = annualLeaveGrantService.grantFiscal(baseDate);
        log.info("Leave grant FISCAL: success={}, skip={}, errors={}", fiscal.successCount, fiscal.skipCount, fiscal.errorCount);

        AnnualLeaveGrantService.BatchResult monthlyTotal = new AnnualLeaveGrantService.BatchResult();
        for (LeavePolicy policy : leavePolicyRepository.findAll().stream()
                .filter(p -> "N".equals(p.getDelYn()))
                .toList()) {
            try {
                monthlyTotal.merge(monthlyLeaveGrantWorker.grantForCompanyMonthly(policy, baseDate));
            } catch (Exception e) {
                log.error("Monthly leave grant failed companyId={}", policy.getCompanyId(), e);
                monthlyTotal.errorCount++;
            }
        }
        log.info("Leave grant MONTHLY: success={}, skip={}, errors={}", monthlyTotal.successCount, monthlyTotal.skipCount, monthlyTotal.errorCount);
    }

    // 회사별 호출, 글로벌 동작 후 정책 필터링으로 회사 1곳만 월차 처리
    public void runForCompany(UUID companyId, LocalDate baseDate) {
        log.info("LeaveGrantWorker[company] start companyId={} baseDate={}", companyId, baseDate);

        AnnualLeaveGrantService.BatchResult hire = annualLeaveGrantService.grantHireDate(baseDate);
        log.info("companyId={} HIRE_DATE: success={}, skip={}, errors={}",
                companyId, hire.successCount, hire.skipCount, hire.errorCount);

        AnnualLeaveGrantService.BatchResult fiscal = annualLeaveGrantService.grantFiscal(baseDate);
        log.info("companyId={} FISCAL: success={}, skip={}, errors={}",
                companyId, fiscal.successCount, fiscal.skipCount, fiscal.errorCount);

        AnnualLeaveGrantService.BatchResult monthlyTotal = new AnnualLeaveGrantService.BatchResult();
        for (LeavePolicy policy : leavePolicyRepository.findAll().stream()
                .filter(p -> "N".equals(p.getDelYn()))
                .filter(p -> companyId.equals(p.getCompanyId()))
                .toList()) {
            try {
                monthlyTotal.merge(monthlyLeaveGrantWorker.grantForCompanyMonthly(policy, baseDate));
            } catch (Exception e) {
                log.error("companyId={} 월차 부여 실패", policy.getCompanyId(), e);
                monthlyTotal.errorCount++;
            }
        }
        log.info("companyId={} MONTHLY: success={}, skip={}, errors={}",
                companyId, monthlyTotal.successCount, monthlyTotal.skipCount, monthlyTotal.errorCount);
    }
}
