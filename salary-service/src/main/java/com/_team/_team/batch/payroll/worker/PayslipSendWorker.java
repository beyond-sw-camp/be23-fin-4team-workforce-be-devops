package com._team._team.batch.payroll.worker;

import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.util.PaymentDateCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 급여명세서 발송 배치 워커
// 매일 09:00 폴링, 회사별 보정 지급일과 오늘 일치하면 알림 발행
@Slf4j
@Component
public class PayslipSendWorker {

    private final PayrollRepository payrollRepository;
    private final SalaryPolicyRepository salaryPolicyRepository;
    private final CompanyHolidayRepository companyHolidayRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public PayslipSendWorker(PayrollRepository payrollRepository,
                             SalaryPolicyRepository salaryPolicyRepository,
                             CompanyHolidayRepository companyHolidayRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.payrollRepository = payrollRepository;
        this.salaryPolicyRepository = salaryPolicyRepository;
        this.companyHolidayRepository = companyHolidayRepository;
        this.eventPublisher = eventPublisher;
    }

    // 오늘이 회사 보정 지급일인 회사만 발송
    @Transactional(readOnly = true)
    public void run() {
        LocalDate today = LocalDate.now();
        log.info("[PayslipSend] start today={}", today);

        // 회사별 CONFIRMED Payroll 그룹핑
        Map<UUID, List<Payroll>> byCompany = payrollRepository
                .findByPayrollStatusAndDelYn(PayrollStatus.CONFIRMED, "N")
                .stream()
                .collect(Collectors.groupingBy(Payroll::getCompanyId));

        if (byCompany.isEmpty()) {
            log.info("[PayslipSend] no targets today={}", today);
            return;
        }

        int sentCompanies = 0;
        int sentMembers = 0;

        for (Map.Entry<UUID, List<Payroll>> e : byCompany.entrySet()) {
            UUID companyId = e.getKey();
            List<Payroll> payrolls = e.getValue();

            // 회사 활성 급여정책 조회, 없으면 skip
            SalaryPolicy policy = salaryPolicyRepository
                    .findByCompanyIdAndDelYn(companyId, "N")
                    .stream().findFirst().orElse(null);
            if (policy == null || policy.getPayDay() == null) {
                continue;
            }

            // 오늘 기준 보정 지급일 계산, 휴일/주말이면 정책 룰대로 직전/직후 영업일
            LocalDate adjustedPayDay = PaymentDateCalculator.calculate(
                    YearMonth.from(today),
                    policy.getPayDay(),
                    policy.getPayDayShiftRule(),
                    companyId,
                    companyHolidayRepository);

            if (!today.equals(adjustedPayDay)) {
                continue;
            }

            // 일치 - 회사 직원에게 명세서 발송 알림
            for (Payroll payroll : payrolls) {
                publishPayslipNotification(payroll);
                sentMembers++;
            }
            sentCompanies++;
            log.info("[PayslipSend] sent companyId={} payrolls={}", companyId, payrolls.size());
        }

        log.info("[PayslipSend] done today={} companies={} members={}",
                today, sentCompanies, sentMembers);
    }

    // 회사별 수동 호출, 강제 발송 - 보정 지급일 검증 없이 그 회사 CONFIRMED 전체 발송
    @Transactional(readOnly = true)
    public void runForCompany(UUID companyId) {
        List<Payroll> targets = payrollRepository
                .findByPayrollStatusAndDelYn(PayrollStatus.CONFIRMED, "N")
                .stream()
                .filter(p -> companyId.equals(p.getCompanyId()))
                .toList();

        for (Payroll payroll : targets) {
            publishPayslipNotification(payroll);
        }
        log.info("[PayslipSend][manual] companyId={} sent={}", companyId, targets.size());
    }

    // 명세서 발송 알림 이벤트 발행, 직원이 알림 클릭 -> 명세 상세 -> PDF 다운로드
    private void publishPayslipNotification(Payroll payroll) {
        String content = String.format(
                "[급여 명세서] %s 급여가 발행되었습니다. 명세서를 확인해 주세요.",
                payroll.getTargetYearMonth() != null ? payroll.getTargetYearMonth() : "이번 달");

        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(payroll.getMemberId())
                .senderId(null)
                .notificationType(NotificationType.SALARY_PUBLISHED)
                .content(content)
                .targetId(payroll.getPayrollId())
                .targetType("PAYROLL")
                .build());
    }
}
