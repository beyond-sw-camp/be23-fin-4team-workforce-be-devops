package com._team._team.contract.service;

import com._team._team.contract.domain.ContractTemplate;
import com._team._team.contract.domain.enums.ContractType;
import com._team._team.contract.dto.reqdto.ContractTemplateCreateReqDto;
import com._team._team.contract.dto.reqdto.ContractTemplateUpdateReqDto;
import com._team._team.contract.dto.resdto.ContractTemplateResDto;
import com._team._team.contract.repository.ContractTemplateRepository;
import com._team._team.dto.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ContractTemplateService {

    private final ContractTemplateRepository templateRepository;

    @Autowired
    public ContractTemplateService(ContractTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    // 템플릿 생성
    public ContractTemplateResDto create(UUID companyId, ContractTemplateCreateReqDto reqDto) {
        ContractTemplate template = ContractTemplate.builder()
                .companyId(companyId)
                .templateName(reqDto.getTemplateName())
                .contractType(reqDto.getContractType())
                .formSchema(reqDto.getFormSchema())
                .build();

        ContractTemplate saved = templateRepository.save(template);
        return ContractTemplateResDto.fromEntity(saved);
    }

    // 템플릿 수정
    public ContractTemplateResDto update(UUID companyId, UUID templateId, ContractTemplateUpdateReqDto reqDto) {
        ContractTemplate template = findTemplateWithPermission(companyId, templateId);

        if (reqDto.getTemplateName() != null) {
            template.updateTemplateName(reqDto.getTemplateName());
        }
        if (reqDto.getFormSchema() != null) {
            template.updateFormSchema(reqDto.getFormSchema());
        }

        return ContractTemplateResDto.fromEntity(template);
    }

    // 템플릿 단건 조회
    @Transactional(readOnly = true)
    public ContractTemplateResDto findById(UUID companyId, UUID templateId) {
        ContractTemplate template = findTemplateWithPermission(companyId, templateId);
        return ContractTemplateResDto.fromEntity(template);
    }

    // 회사별 전체 템플릿 조회 (관리자용)
    @Transactional(readOnly = true)
    public List<ContractTemplateResDto> findAll(UUID companyId) {
        return templateRepository.findByCompanyIdAndDelYn(companyId, "N").stream()
                .map(ContractTemplateResDto::fromEntity)
                .toList();
    }

    // 활성 템플릿만 조회
    @Transactional(readOnly = true)
    public List<ContractTemplateResDto> findActive(UUID companyId) {
        return templateRepository.findByCompanyIdAndIsActiveYnAndDelYn(companyId, "Y", "N").stream()
                .map(ContractTemplateResDto::fromEntity)
                .toList();
    }

    // 활성화
    public ContractTemplateResDto activate(UUID companyId, UUID templateId) {
        ContractTemplate template = findTemplateWithPermission(companyId, templateId);
        template.activate();
        return ContractTemplateResDto.fromEntity(template);
    }

    // 비활성화
    public ContractTemplateResDto deactivate(UUID companyId, UUID templateId) {
        ContractTemplate template = findTemplateWithPermission(companyId, templateId);
        template.deactivate();
        return ContractTemplateResDto.fromEntity(template);
    }

    // 회사 생성 시 기본 템플릿 자동 생성
    public void initDefaultTemplates(UUID companyId) {
        templateRepository.save(ContractTemplate.builder()
                .companyId(companyId)
                .templateName("근로계약서")
                .contractType(ContractType.EMPLOYMENT)
                .formSchema(defaultEmploymentSchema())
                .build());

        templateRepository.save(ContractTemplate.builder()
                .companyId(companyId)
                .templateName("연봉계약서")
                .contractType(ContractType.SALARY)
                .formSchema(defaultSalarySchema())
                .build());

        templateRepository.save(ContractTemplate.builder()
                .companyId(companyId)
                .templateName("비밀유지서약서")
                .contractType(ContractType.NDA)
                .formSchema(defaultNdaSchema())
                .build());

        templateRepository.save(ContractTemplate.builder()
                .companyId(companyId)
                .templateName("개인정보 수집·이용 동의서")
                .contractType(ContractType.PRIVACY_CONSENT)
                .formSchema(defaultPrivacySchema())
                .build());
    }

    // === private ===

    private ContractTemplate findTemplateWithPermission(UUID companyId, UUID templateId) {
        ContractTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "계약서 템플릿을 찾을 수 없습니다."));

        if (!template.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        if ("Y".equals(template.getDelYn())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "삭제된 템플릿입니다.");
        }

        return template;
    }

    private String defaultEmploymentSchema() {
        return """
                {"fields":[
                  {"key":"employeeName","label":"성명","type":"text","source":"AUTO","sourceField":"name","editable":false},
                  {"key":"sabun","label":"사번","type":"text","source":"AUTO","sourceField":"sabun","editable":false},
                  {"key":"organizationName","label":"부서","type":"text","source":"AUTO","sourceField":"organizationName","editable":false},
                  {"key":"jobTitleName","label":"직책","type":"text","source":"AUTO","sourceField":"jobTitleName","editable":false},
                  {"key":"baseSalary","label":"연봉","type":"number","source":"AUTO","sourceField":"baseSalary","editable":false},
                  {"key":"contractStartDate","label":"계약 시작일","type":"date","source":"ADMIN_INPUT","editable":false},
                  {"key":"contractEndDate","label":"계약 종료일","type":"date","source":"ADMIN_INPUT","editable":false},
                  {"key":"workLocation","label":"근무지","type":"text","source":"ADMIN_INPUT","editable":false},
                  {"key":"workHours","label":"근무시간","type":"text","source":"ADMIN_INPUT","editable":false},
                  {"key":"specialTerms","label":"특약사항","type":"textarea","source":"ADMIN_INPUT","editable":false}
                ]}""";
    }

    private String defaultSalarySchema() {
        return """
                {"fields":[
                  {"key":"employeeName","label":"성명","type":"text","source":"AUTO","sourceField":"name","editable":false},
                  {"key":"sabun","label":"사번","type":"text","source":"AUTO","sourceField":"sabun","editable":false},
                  {"key":"organizationName","label":"부서","type":"text","source":"AUTO","sourceField":"organizationName","editable":false},
                  {"key":"jobTitleName","label":"직책","type":"text","source":"AUTO","sourceField":"jobTitleName","editable":false},
                  {"key":"currentSalary","label":"현재 연봉","type":"number","source":"AUTO","sourceField":"baseSalary","editable":false},
                  {"key":"newSalary","label":"변경 연봉","type":"number","source":"ADMIN_INPUT","editable":false},
                  {"key":"effectiveDate","label":"적용일","type":"date","source":"ADMIN_INPUT","editable":false},
                  {"key":"specialTerms","label":"특약사항","type":"textarea","source":"ADMIN_INPUT","editable":false}
                ]}""";
    }

    private String defaultNdaSchema() {
        return """
                {"fields":[
                  {"key":"employeeName","label":"성명","type":"text","source":"AUTO","sourceField":"name","editable":false},
                  {"key":"sabun","label":"사번","type":"text","source":"AUTO","sourceField":"sabun","editable":false},
                  {"key":"organizationName","label":"부서","type":"text","source":"AUTO","sourceField":"organizationName","editable":false},
                  {"key":"ndaScope","label":"비밀유지 범위","type":"textarea","source":"ADMIN_INPUT","editable":false}
                ]}""";
    }

    private String defaultPrivacySchema() {
        return """
                {"fields":[
                  {"key":"employeeName","label":"성명","type":"text","source":"AUTO","sourceField":"name","editable":false},
                  {"key":"sabun","label":"사번","type":"text","source":"AUTO","sourceField":"sabun","editable":false},
                  {"key":"collectionItems","label":"수집 항목","type":"textarea","source":"ADMIN_INPUT","editable":false},
                  {"key":"collectionPurpose","label":"수집 목적","type":"textarea","source":"ADMIN_INPUT","editable":false},
                  {"key":"retentionPeriod","label":"보유 기간","type":"text","source":"ADMIN_INPUT","editable":false}
                ]}""";
    }

}
