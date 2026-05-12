package com._team._team.contract.service;

import com._team._team.contract.domain.Contract;
import com._team._team.contract.domain.ContractTemplate;
import com._team._team.contract.domain.enums.ContractStatus;
import com._team._team.contract.domain.enums.ContractType;
import com._team._team.contract.repository.ContractRepository;
import com._team._team.contract.repository.ContractTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 시드 전용 - 결재 우회하고 SIGNED 상태로 직접 Contract insert.
 * DemoContractTemplateSeedRunner 가 native query 로 직원/Salary 조회 후 호출.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractSeedService {

    private final ContractRepository contractRepository;
    private final ContractTemplateRepository contractTemplateRepository;

    /**
     * 직원별 매년 SALARY Contract 1건 생성 (SIGNED). 멱등.
     */
    @Transactional
    public void seedSalaryContract(UUID companyId, UUID memberId, int year, long baseSalary,
                                   LocalDate effectiveFrom, String employeeName, String employeeSabun,
                                   String organizationName, String jobTitleName) {
        List<ContractTemplate> templates = contractTemplateRepository
                .findByCompanyIdAndContractTypeAndDelYn(companyId, ContractType.SALARY, "N");
        if (templates.isEmpty()) {
            log.warn("[CONTRACT-SEED] SALARY 템플릿 없음 companyId={}", companyId);
            return;
        }
        ContractTemplate template = templates.get(0);

        String sabunPart = (employeeSabun == null || employeeSabun.isBlank())
                ? memberId.toString().substring(0, 8)
                : employeeSabun;
        String contractNumber = String.format("연봉-%s-%s", effectiveFrom, sabunPart);

        // UNIQUE 제약은 delYn 무관이라 existsByContractNumber 직접 체크 (soft-delete된 행도 키 점유)
        if (contractRepository.existsByContractNumber(contractNumber)) return;

        long annualSalary = baseSalary * 12L;
        String contentJson = String.format(
                "{\"year\":%d,\"annualSalary\":%d,\"monthlyBase\":%d,\"effectiveFrom\":\"%s\"}",
                year, annualSalary, baseSalary, effectiveFrom);

        Contract contract = Contract.builder()
                .companyId(companyId)
                .contractTemplate(template)
                .employeeMemberId(memberId)
                .contractType(ContractType.SALARY)
                .contentJson(contentJson)
                .contractStatus(ContractStatus.SIGNED)
                .employeeName(employeeName)
                .employeeSabun(employeeSabun)
                .organizationName(organizationName)
                .jobTitleName(jobTitleName)
                .contractNumber(contractNumber)
                .build();
        contractRepository.save(contract);
    }
}
