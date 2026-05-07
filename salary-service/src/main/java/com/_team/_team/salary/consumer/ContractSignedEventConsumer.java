package com._team._team.salary.consumer;

import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com._team._team.event.ContractSignedEvent;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
public class ContractSignedEventConsumer {

    private final SalaryRepository salaryRepository;
    private final SalaryPolicyRepository salaryPolicyRepository;

    @Autowired
    public ContractSignedEventConsumer(SalaryRepository salaryRepository, SalaryPolicyRepository salaryPolicyRepository) {
        this.salaryRepository = salaryRepository;
        this.salaryPolicyRepository = salaryPolicyRepository;
    }

    @KafkaListener(
            topics = "contract-signed",
            groupId = "salary-service",
            properties = {
                    "spring.json.value.default.type=com._team._team.event.ContractSignedEvent"
            }
    )
    @Transactional
    public void consume(ContractSignedEvent event) {
        log.info("[Contract→Salary] 수신. contractId={}, memberId={}, newSalary={}, effectiveDate={}",
                event.getContractId(), event.getMemberId(),
                event.getNewSalary(), event.getEffectiveDate());

        if (!"SALARY".equals(event.getContractType())) {
            log.info("[Contract→Salary] SALARY 타입이 아님. 무시. type={}", event.getContractType());
            return;
        }

        if (event.getNewSalary() == null || event.getEffectiveDate() == null) {
            log.warn("[Contract→Salary] newSalary 또는 effectiveDate 누락. contractId={}",
                    event.getContractId());
            return;
        }

        LocalDate effectiveDate = event.getEffectiveDate();

        // 기존 활성 급여 조회 — 정책·부양가족 정보 승계용
        Salary prev = salaryRepository
                .findActiveSalary(event.getMemberId(), event.getCompanyId(), effectiveDate)
                .orElse(null);

        UUID policyId;
        Integer dependentCount = 1;
        Integer childUnder20Count = 0;

        if (prev != null) {
            policyId = prev.getSalaryPolicyId();
            dependentCount = prev.getDependentCount();
            childUnder20Count = prev.getChildUnder20Count();
            // 기존 급여 마감
            prev.closeEffectivePeriod(effectiveDate.minusDays(1));
            log.info("[Contract→Salary] 기존 급여 마감. salaryId={}, closedTo={}",
                    prev.getSalaryId(), effectiveDate.minusDays(1));
        } else {
            // 기존 급여 없으면 회사 활성 정책에서 가져옴
            policyId = salaryPolicyRepository
                    .findActivePolicies(event.getCompanyId(), effectiveDate)
                    .stream().findFirst()
                    .map(SalaryPolicy::getSalaryPolicyId)
                    .orElse(null);
        }

        if (policyId == null) {
            log.error("[Contract→Salary] 급여 정책을 찾을 수 없음. memberId={}, companyId={}",
                    event.getMemberId(), event.getCompanyId());
            return;
        }

        Salary newSalary = Salary.builder()
                .memberId(event.getMemberId())
                .companyId(event.getCompanyId())
                .salaryPolicyId(policyId)
                .baseSalary(event.getNewSalary())
                .effectiveFrom(effectiveDate)
                .effectiveTo(null)
                .dependentCount(dependentCount)
                .childUnder20Count(childUnder20Count)
                .build();

        Salary saved = salaryRepository.save(newSalary);
        log.info("[Contract→Salary] 새 급여 생성 완료. salaryId={}, memberId={}, baseSalary={}, from={}",
                saved.getSalaryId(), event.getMemberId(), event.getNewSalary(), effectiveDate);
    }
}
