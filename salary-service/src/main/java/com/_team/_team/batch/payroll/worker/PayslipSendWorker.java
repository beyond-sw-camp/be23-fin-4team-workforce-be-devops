package com._team._team.batch.payroll.worker;

import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.enums.PayrollStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// 급여명세서 발송 배치 워커
// 확정 Payroll 대상 조회, 템플릿 렌더링
@Slf4j
@Component
public class PayslipSendWorker {

    private final PayrollRepository payrollRepository;

    @Autowired
    public PayslipSendWorker(PayrollRepository payrollRepository) {
        this.payrollRepository = payrollRepository;
    }

    // 글로벌 호출
    @Transactional(readOnly = true)
    public void run() {
        List<Payroll> targets = payrollRepository.findByPayrollStatusAndDelYn(PayrollStatus.CONFIRMED, "N");
        log.info("PayslipSendWorker: CONFIRMED {}건, 발송 연동 전이라 로그만", targets.size());
    }

    // 회사별 호출
    @Transactional(readOnly = true)
    public void runForCompany(UUID companyId) {
        List<Payroll> all = payrollRepository.findByPayrollStatusAndDelYn(PayrollStatus.CONFIRMED, "N");
        long count = all.stream().filter(p -> companyId.equals(p.getCompanyId())).count();
        log.info("PayslipSendWorker: companyId={} CONFIRMED {}건, 발송 연동 전이라 로그만", companyId, count);
    }
}
