package com._team._team.contract.service;

import com._team._team.approval.feignclients.MemberServiceClient;
import com._team._team.approval.feignclients.dto.MemberContractInfoResDto;
import com._team._team.contract.domain.Contract;
import com._team._team.contract.domain.ContractBatch;
import com._team._team.contract.domain.ContractParty;
import com._team._team.contract.domain.ContractTemplate;
import com._team._team.contract.domain.enums.ContractStatus;
import com._team._team.contract.domain.enums.ContractType;
import com._team._team.contract.domain.enums.PartyRole;
import com._team._team.contract.domain.enums.SignStatus;
import com._team._team.contract.dto.reqdto.*;
import com._team._team.contract.dto.resdto.ContractBatchResDto;
import com._team._team.contract.dto.resdto.ContractResDto;
import com._team._team.contract.feignclients.SalaryServiceClient;
import com._team._team.contract.feignclients.dto.SalaryApiResponse;
import com._team._team.contract.feignclients.dto.SalaryInfoResDto;
import com._team._team.contract.repository.ContractBatchRepository;
import com._team._team.contract.repository.ContractPartyRepository;
import com._team._team.contract.repository.ContractRepository;
import com._team._team.contract.repository.ContractTemplateRepository;
import com._team._team.dto.BusinessException;
import com._team._team.event.ContractSignedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ContractService {

    private final ContractRepository contractRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractBatchRepository batchRepository;
    private final ContractPartyRepository partyRepository;
    private final SalaryServiceClient salaryServiceClient;
    private final MemberServiceClient memberServiceClient;
    private final ContractNotificationService contractNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContractNumberService contractNumberService;
    private final ContractSignedEventPublisher contractSignedEventPublisher;

    @Autowired
    public ContractService(ContractRepository contractRepository, ContractTemplateRepository templateRepository, ContractBatchRepository batchRepository, ContractPartyRepository partyRepository, SalaryServiceClient salaryServiceClient, MemberServiceClient memberServiceClient, ContractNotificationService contractNotificationService, ContractNumberService contractNumberService, ContractSignedEventPublisher contractSignedEventPublisher) {
        this.contractRepository = contractRepository;
        this.templateRepository = templateRepository;
        this.batchRepository = batchRepository;
        this.partyRepository = partyRepository;
        this.salaryServiceClient = salaryServiceClient;
        this.memberServiceClient = memberServiceClient;
        this.contractNotificationService = contractNotificationService;
        this.contractNumberService = contractNumberService;
        this.contractSignedEventPublisher = contractSignedEventPublisher;
    }

    //    개별 발송
    public ContractResDto sendToEmployee(UUID companyId, UUID senderMemberId, ContractSendReqDto reqDto) {
        log.info("[Contract-Send] adminInputJson={}", reqDto.getAdminInputJson());
        ContractTemplate template = findActiveTemplate(companyId, reqDto.getTemplateId());

        String sealImageUrl = memberServiceClient.getCompanySealImageUrl(companyId);

        // salary/member 데이터로 AUTO 필드 채우기
        SalaryInfoResDto salaryInfo = getCurrentSalary(companyId, reqDto.getEmployeeMemberId());
        log.info("salaryInfo: baseSalary={}", salaryInfo.getBaseSalary());

        // member 데이터 (인사 정보)
        MemberContractInfoResDto memberInfo = memberServiceClient.getMemberContractInfo(reqDto.getEmployeeMemberId());
        log.info("memberInfo: name={}, sabun={}, org={}, jobTitle={}",
                memberInfo.getName(), memberInfo.getSabun(),
                memberInfo.getOrganizationName(), memberInfo.getJobTitleName());

        // contentJson 생성 (AUTO + ADMIN_INPUT 합치기)
        String contentJson = buildContentJson(template.getFormSchema(), salaryInfo, memberInfo, reqDto.getAdminInputJson());

        // 문서번호 생성
        String contractNumber = contractNumberService.generate(template.getContractType());

        // Contract 생성
        Contract contract = Contract.builder()
                .companyId(companyId)
                .contractTemplate(template)
                .employeeMemberId(reqDto.getEmployeeMemberId())
                .contractType(template.getContractType())
                .contentJson(contentJson)
                .formSchemaSnapshot(template.getFormSchema())
                .contractStatus(ContractStatus.SENT)
                .contractNumber(contractNumber)
                .sealImageUrl(sealImageUrl)
                .employeeName(memberInfo.getName())           // member에서
                .employeeSabun(memberInfo.getSabun())          // member에서
                .organizationName(memberInfo.getOrganizationName()) // member에서
                .jobTitleName(memberInfo.getJobTitleName())     // member에서
                .build();

        Contract saved = contractRepository.save(contract);

        // ContractParty 2건 (회사측 자동서명 + 직원측 대기)
        ContractParty companyParty = ContractParty.builder()
                .contract(saved)
                .memberId(senderMemberId)
                .partyRole(PartyRole.COMPANY)
                .signStatus(SignStatus.SIGNED)
                .build();
        companyParty.sign(null); // 회사측은 발송 시 자동 서명

        ContractParty employeeParty = ContractParty.builder()
                .contract(saved)
                .memberId(reqDto.getEmployeeMemberId())
                .partyRole(PartyRole.EMPLOYEE)
                .build();

        List<ContractParty> savedParties = partyRepository.saveAll(List.of(companyParty, employeeParty));

        // 직원에게 계약서 도착 알림
        contractNotificationService.notifyContractSent(
                reqDto.getEmployeeMemberId(),
                senderMemberId,
                saved.getContractId(),
                template.getTemplateName());

        return ContractResDto.fromEntity(saved, savedParties);
    }

    //    일괄 발송
    public ContractBatchResDto sendBatch(UUID companyId, UUID senderMemberId, ContractBatchSendReqDto reqDto) {
        ContractTemplate template = findActiveTemplate(companyId, reqDto.getTemplateId());

        String sealImageUrl = memberServiceClient.getCompanySealImageUrl(companyId);

//        배치 생성
        ContractBatch batch = ContractBatch.builder()
                .companyId(companyId)
                .contractTemplate(template)
                .batchName(reqDto.getBatchName())
                .totalCount(reqDto.getItems().size())
                .createdBy(senderMemberId)
                .build();

        ContractBatch savedBatch = batchRepository.save(batch);


        // 직원별 계약 생성
        for (ContractBatchSendReqDto.BatchItem item : reqDto.getItems()) {
            SalaryInfoResDto salaryInfo = getCurrentSalary(companyId, item.getEmployeeMemberId());
            MemberContractInfoResDto memberInfo = memberServiceClient.getMemberContractInfo(item.getEmployeeMemberId());
            String contentJson = buildContentJson(template.getFormSchema(), salaryInfo, memberInfo, item.getAdminInputJson());

            // 문서번호 생성
            String contractNumber = contractNumberService.generate(template.getContractType());

            Contract contract = Contract.builder()
                    .companyId(companyId)
                    .contractTemplate(template)
                    .contractBatch(savedBatch)
                    .employeeMemberId(item.getEmployeeMemberId())
                    .contractType(template.getContractType())
                    .contentJson(contentJson)
                    .formSchemaSnapshot(template.getFormSchema())
                    .contractStatus(ContractStatus.SENT)
                    .contractNumber(contractNumber)
                    .sealImageUrl(sealImageUrl)
                    .employeeName(memberInfo.getName())
                    .employeeSabun(memberInfo.getSabun())
                    .organizationName(memberInfo.getOrganizationName())
                    .jobTitleName(memberInfo.getJobTitleName())
                    .build();

            Contract savedContract = contractRepository.save(contract);

            // 회사측 자동서명 + 직원측 대기
            ContractParty companyParty = ContractParty.builder()
                    .contract(savedContract)
                    .memberId(senderMemberId)
                    .partyRole(PartyRole.COMPANY)
                    .signStatus(SignStatus.SIGNED)
                    .build();
            companyParty.sign(null);

            ContractParty employeeParty = ContractParty.builder()
                    .contract(savedContract)
                    .memberId(item.getEmployeeMemberId())
                    .partyRole(PartyRole.EMPLOYEE)
                    .build();

            partyRepository.saveAll(List.of(companyParty, employeeParty));

            // 각 직원에게 계약서 도착 알림
            contractNotificationService.notifyContractSent(
                    item.getEmployeeMemberId(),
                    senderMemberId,
                    savedContract.getContractId(),
                    template.getTemplateName());
        }

        return ContractBatchResDto.fromEntity(savedBatch);
    }

//    직원 서명
    public ContractResDto sign(UUID companyId, UUID memberId, UUID contractId, ContractSignReqDto reqDto){
        Contract contract = findContractWithPermission(companyId, contractId);

        if (contract.getContractStatus() != ContractStatus.SENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "서명할 수 있는 상태가 아닙니다.");
        }

        // 본인 파티 찾기
        ContractParty party = partyRepository.findByContractContractIdAndMemberId(contractId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "서명 권한이 없습니다."));

        if (party.getSignStatus() == SignStatus.SIGNED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이미 서명했습니다.");
        }

        // 서명 처리
        party.sign(reqDto.getSignatureImageUrl());

        List<ContractParty> parties = partyRepository.findByContractContractId(contractId);

        // 전원 서명 완료 체크
        boolean allSigned = !partyRepository.existsByContractContractIdAndSignStatusNot(contractId, SignStatus.SIGNED);

        if (allSigned) {
            contract.sign();

            if (contract.getContractBatch() != null) {
                contract.getContractBatch().incrementSignedCount();
            }

            // 발송자(인사팀)에게 서명 완료 알림
            // 회사측 파티에서 발송자 memberId 조회
            ContractParty companyPartyEntity = parties.stream()
                    .filter(p -> p.getPartyRole() == PartyRole.COMPANY)
                    .findFirst()
                    .orElse(null);

            if (companyPartyEntity != null) {
                contractNotificationService.notifyContractSigned(
                        companyPartyEntity.getMemberId(),
                        memberId,
                        contractId,
                        contract.getEmployeeName());
            }

            // 연봉계약서 서명 완료 시 급여 반영 이벤트 발행
            if (contract.getContractType() == ContractType.SALARY) {
                publishSalaryContractEvent(contract);
            }


            // TODO: PDF 생성 + S3 저장
        }

        return ContractResDto.fromEntity(contract, parties);
    }

    private void publishSalaryContractEvent(Contract contract) {
        try {
            Map<String, Object> content = objectMapper.readValue(
                    contract.getContentJson(),
                    new TypeReference<Map<String, Object>>() {});

            Object newSalary = content.get("newSalary");
            Object effectiveDate = content.get("effectiveDate");

            if (newSalary == null || effectiveDate == null) {
                log.warn("[Contract] 연봉계약서에 newSalary 또는 effectiveDate 누락. contractId={}",
                        contract.getContractId());
                return;
            }

            ContractSignedEvent event = ContractSignedEvent.builder()
                    .companyId(contract.getCompanyId())
                    .memberId(contract.getEmployeeMemberId())
                    .contractId(contract.getContractId())
                    .contractType("SALARY")
                    .newSalary(Long.valueOf(newSalary.toString()))
                    .effectiveDate(java.time.LocalDate.parse(effectiveDate.toString()))
                    .signedAt(java.time.LocalDateTime.now())
                    .build();

            contractSignedEventPublisher.publish(event);
        } catch (Exception e) {
            log.error("[Contract] 급여 반영 이벤트 생성 실패. contractId={}",
                    contract.getContractId(), e);
        }
    }

    // 직원 거절
    public ContractResDto reject(UUID companyId, UUID memberId, UUID contractId, ContractRejectReqDto reqDto) {
        Contract contract = findContractWithPermission(companyId, contractId);

        if (contract.getContractStatus() != ContractStatus.SENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "거절할 수 있는 상태가 아닙니다.");
        }

        // 본인 파티 찾기
        ContractParty party = partyRepository.findByContractContractIdAndMemberId(contractId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "거절 권한이 없습니다."));

        if (party.getPartyRole() == PartyRole.COMPANY) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "회사측은 거절할 수 없습니다.");
        }

        if (party.getSignStatus() != SignStatus.PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이미 처리된 계약입니다.");
        }

        // 거절 처리
        party.reject();
        contract.reject(reqDto.getRejectReason());

        if (contract.getContractBatch() != null) {
            contract.getContractBatch().incrementRejectedCount();
        }

        // 발송자(인사팀)에게 거절 알림
        List<ContractParty> parties = partyRepository.findByContractContractId(contractId);

        ContractParty companyPartyEntity = parties.stream()
                .filter(p -> p.getPartyRole() == PartyRole.COMPANY)
                .findFirst()
                .orElse(null);

        if (companyPartyEntity != null) {
            contractNotificationService.notifyContractRejected(
                    companyPartyEntity.getMemberId(),
                    memberId,
                    contractId,
                    contract.getEmployeeName(),
                    reqDto.getRejectReason());
        }

        return ContractResDto.fromEntity(contract, parties);
    }

    // ===================== 조회 =====================

    // 직원 본인 계약 목록
    @Transactional(readOnly = true)
    public List<ContractResDto> findMyContracts(UUID memberId, ContractStatus status) {
        List<Contract> contracts;
        if (status != null) {
            contracts = contractRepository.findByEmployeeMemberIdAndContractStatusAndDelYnOrderByCreatedAtDesc(
                    memberId, status, "N");
        } else {
            contracts = contractRepository.findByEmployeeMemberIdAndDelYnOrderByCreatedAtDesc(memberId, "N");
        }
        List<UUID> contractIds = contracts.stream().map(Contract::getContractId).toList();
        Map<UUID, List<ContractParty>> partyMap = partyRepository.findByContractIdIn(contractIds)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getContract().getContractId()));

        return contracts.stream()
                .map(c -> ContractResDto.fromEntity(c, partyMap.getOrDefault(c.getContractId(), List.of())))
                .toList();
    }

    // 계약 상세 조회
    @Transactional(readOnly = true)
    public ContractResDto findById(UUID companyId, UUID contractId) {
        Contract contract = findContractWithPermission(companyId, contractId);
        List<ContractParty> parties = partyRepository.findByContractContractId(contractId);
        return ContractResDto.fromEntity(contract, parties);
    }

    // 직원용 계약 상세 (본인 확인)
    @Transactional(readOnly = true)
    public ContractResDto findMyContractById(UUID companyId, UUID memberId, UUID contractId) {
        Contract contract = findContractWithPermission(companyId, contractId);

        if (!contract.getEmployeeMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인의 계약만 조회할 수 있습니다.");
        }

        List<ContractParty> parties = partyRepository.findByContractContractId(contractId);
        return ContractResDto.fromEntity(contract, parties);
    }


    // 배치별 계약 목록 (인사팀용)
    @Transactional(readOnly = true)
    public List<ContractResDto> findByBatchId(UUID companyId, UUID batchId) {
        ContractBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "배치를 찾을 수 없습니다."));

        if (!batch.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        List<Contract> contracts = contractRepository.findByContractBatchBatchIdAndDelYn(batchId, "N");
        List<UUID> contractIds = contracts.stream().map(Contract::getContractId).toList();
        Map<UUID, List<ContractParty>> partyMap = partyRepository.findByContractIdIn(contractIds)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getContract().getContractId()));

        return contracts.stream()
                .map(c -> ContractResDto.fromEntity(c, partyMap.getOrDefault(c.getContractId(), List.of())))
                .toList();
    }

    // 배치 목록 (인사팀용)
    @Transactional(readOnly = true)
    public List<ContractBatchResDto> findBatches(UUID companyId) {
        return batchRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(ContractBatchResDto::fromEntity)
                .toList();
    }

    // 회사별 전체 계약 목록 (인사팀용)
    @Transactional(readOnly = true)
    public List<ContractResDto> findAllByCompanyId(UUID companyId) {
        List<Contract> contracts = contractRepository.findByCompanyIdAndDelYnOrderByCreatedAtDesc(companyId, "N");

        List<UUID> contractIds = contracts.stream().map(Contract::getContractId).toList();
        Map<UUID, List<ContractParty>> partyMap = partyRepository.findByContractIdIn(contractIds)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getContract().getContractId()));

        return contracts.stream()
                .map(c -> ContractResDto.fromEntity(c, partyMap.getOrDefault(c.getContractId(), List.of())))
                .toList();
    }



    private ContractTemplate findActiveTemplate(UUID companyId, UUID templateId) {
        ContractTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "계약서 템플릿을 찾을 수 없습니다."));

        if (!template.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        if ("N".equals(template.getIsActiveYn())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비활성화된 템플릿입니다.");
        }

        return template;
    }

    private Contract findContractWithPermission(UUID companyId, UUID contractId) {
        Contract contract = contractRepository.findByIdWithTemplate(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "계약을 찾을 수 없습니다."));

        if (!contract.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        return contract;
    }

    private SalaryInfoResDto getCurrentSalary(UUID companyId, UUID memberId) {
        SalaryApiResponse response;
        try {
            response = salaryServiceClient.getSalaryByMemberId(companyId, memberId);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "급여 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "해당 직원의 급여 정보를 찾을 수 없습니다.");
        }

        return response.getData().stream()
                .filter(s -> s.getEffectiveTo() == null)
                .findFirst()
                .orElse(response.getData().get(0));
    }

    private String buildContentJson(String formSchema, SalaryInfoResDto salaryInfo,
                                    MemberContractInfoResDto memberInfo, Map<String, Object> adminInputJson) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("employeeName", memberInfo.getName());
            content.put("sabun", memberInfo.getSabun());
            content.put("organizationName", memberInfo.getOrganizationName());
            content.put("jobTitleName", memberInfo.getJobTitleName());
            content.put("baseSalary", salaryInfo.getBaseSalary());

            if (adminInputJson != null && !adminInputJson.isEmpty()) {
                content.putAll(adminInputJson);
            }

            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "계약서 내용 생성 중 오류가 발생했습니다.");
        }
    }

    // 계약 회수 (인사팀)
    public ContractResDto cancel(UUID companyId, UUID memberId, UUID contractId, String cancelReason) {
        Contract contract = findContractWithPermission(companyId, contractId);

        if (contract.getContractStatus() != ContractStatus.SENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "회수할 수 있는 상태가 아닙니다.");
        }

        // 직원이 이미 서명했는지 확인
        ContractParty employeeParty = partyRepository.findByContractContractId(contractId).stream()
                .filter(p -> p.getPartyRole() == PartyRole.EMPLOYEE)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "직원 파티를 찾을 수 없습니다."));

        if (employeeParty.getSignStatus() == SignStatus.SIGNED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "직원이 이미 서명한 계약은 회수할 수 없습니다.");
        }

        // 회수 처리
        contract.cancel(cancelReason);
        employeeParty.cancel();

        // 배치 카운트 반영
        if (contract.getContractBatch() != null) {
            contract.getContractBatch().incrementRejectedCount();
        }


        // 직원에게 회수 알림
        contractNotificationService.notifyContractCanceled(
                contract.getEmployeeMemberId(),
                memberId,
                contractId,
                contract.getContractTemplate().getTemplateName());

        List<ContractParty> parties = partyRepository.findByContractContractId(contractId);
        return ContractResDto.fromEntity(contract, parties);
    }

    // 개별 재발송
    public ContractResDto resend(UUID companyId, UUID senderMemberId, UUID contractId, Map<String, Object> adminInputJson) {
        Contract original = findContractWithPermission(companyId, contractId);

        // 상태 검증: REJECTED 또는 CANCELED만 재발송 가능
        if (original.getContractStatus() != ContractStatus.REJECTED
                && original.getContractStatus() != ContractStatus.CANCELED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "거절 또는 회수된 계약만 재발송할 수 있습니다.");
        }

        // 재발송 횟수 제한 (최대 5회)
        if (original.getRevision() >= 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "최대 재발송 횟수(5회)를 초과했습니다.");
        }

        // 동일 직원 + 동일 템플릿으로 SENT 상태인 계약 중복 차단
        boolean hasSent = contractRepository.existsByEmployeeMemberIdAndContractTemplateTemplateIdAndContractStatusAndDelYn(
                original.getEmployeeMemberId(),
                original.getContractTemplate().getTemplateId(),
                ContractStatus.SENT,
                "N");
        if (hasSent) {
            throw new BusinessException(HttpStatus.CONFLICT, "해당 직원에게 이미 발송 중인 동일 계약서가 있습니다.");
        }

        ContractTemplate template = original.getContractTemplate();

        // 최신 데이터로 AUTO 필드 갱신
        SalaryInfoResDto salaryInfo = getCurrentSalary(companyId, original.getEmployeeMemberId());
        MemberContractInfoResDto memberInfo = memberServiceClient.getMemberContractInfo(original.getEmployeeMemberId());
        String contentJson = buildContentJson(template.getFormSchema(), salaryInfo, memberInfo, adminInputJson);

        String contractNumber = contractNumberService.generate(template.getContractType());

        String sealImageUrl = memberServiceClient.getCompanySealImageUrl(companyId);

        // 새 Contract 생성 (이력 연결)
        Contract contract = Contract.builder()
                .companyId(companyId)
                .contractTemplate(template)
                .employeeMemberId(original.getEmployeeMemberId())
                .contractType(template.getContractType())
                .contentJson(contentJson)
                .formSchemaSnapshot(template.getFormSchema())
                .contractStatus(ContractStatus.SENT)
                .contractNumber(contractNumber)
                .sealImageUrl(sealImageUrl)
                .employeeName(memberInfo.getName())
                .employeeSabun(memberInfo.getSabun())
                .organizationName(memberInfo.getOrganizationName())
                .jobTitleName(memberInfo.getJobTitleName())
                .previousContractId(original.getContractId())
                .revision(original.getRevision() + 1)
                .build();

        Contract saved = contractRepository.save(contract);

        // ContractParty 생성
        ContractParty companyParty = ContractParty.builder()
                .contract(saved)
                .memberId(senderMemberId)
                .partyRole(PartyRole.COMPANY)
                .signStatus(SignStatus.SIGNED)
                .build();
        companyParty.sign(null);

        ContractParty employeeParty = ContractParty.builder()
                .contract(saved)
                .memberId(original.getEmployeeMemberId())
                .partyRole(PartyRole.EMPLOYEE)
                .build();

        List<ContractParty> savedParties = partyRepository.saveAll(List.of(companyParty, employeeParty));

        // 직원에게 재발송 알림
        contractNotificationService.notifyContractSent(
                original.getEmployeeMemberId(),
                senderMemberId,
                saved.getContractId(),
                template.getTemplateName());

        return ContractResDto.fromEntity(saved, savedParties);
    }

    // 배치 재발송
    public ContractBatchResDto resendBatch(UUID companyId, UUID senderMemberId,
                                           UUID batchId, ContractBatchResendReqDto reqDto) {
        ContractBatch originalBatch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "배치를 찾을 수 없습니다."));

        if (!originalBatch.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        ContractTemplate template = originalBatch.getContractTemplate();

        // 새 배치 생성 (이력 연결)
        ContractBatch newBatch = ContractBatch.builder()
                .companyId(companyId)
                .contractTemplate(template)
                .batchName(reqDto.getBatchName())
                .totalCount(reqDto.getItems().size())
                .createdBy(senderMemberId)
                .previousBatchId(originalBatch.getBatchId())
                .build();

        ContractBatch savedBatch = batchRepository.save(newBatch);

        String sealImageUrl = memberServiceClient.getCompanySealImageUrl(companyId);

        for (ContractBatchResendReqDto.BatchResendItem item : reqDto.getItems()) {
            Contract original = contractRepository.findByIdWithTemplate(item.getContractId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "계약을 찾을 수 없습니다: " + item.getContractId()));

            // 상태 검증
            if (original.getContractStatus() != ContractStatus.REJECTED
                    && original.getContractStatus() != ContractStatus.CANCELED) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "거절 또는 회수된 계약만 재발송할 수 있습니다: " + original.getEmployeeName());
            }

            // 재발송 횟수 제한
            if (original.getRevision() >= 5) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "최대 재발송 횟수(5회)를 초과했습니다: " + original.getEmployeeName());
            }

            // 중복 SENT 차단
            boolean hasSent = contractRepository.existsByEmployeeMemberIdAndContractTemplateTemplateIdAndContractStatusAndDelYn(
                    original.getEmployeeMemberId(),
                    template.getTemplateId(),
                    ContractStatus.SENT,
                    "N");
            if (hasSent) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "이미 발송 중인 동일 계약서가 있습니다: " + original.getEmployeeName());
            }

            // 최신 데이터로 갱신
            SalaryInfoResDto salaryInfo = getCurrentSalary(companyId, original.getEmployeeMemberId());
            MemberContractInfoResDto memberInfo = memberServiceClient.getMemberContractInfo(original.getEmployeeMemberId());
            String contentJson = buildContentJson(template.getFormSchema(), salaryInfo, memberInfo, item.getAdminInputJson());

            // 문서번호 생성
            String contractNumber = contractNumberService.generate(template.getContractType());


            Contract contract = Contract.builder()
                    .companyId(companyId)
                    .contractTemplate(template)
                    .contractBatch(savedBatch)
                    .employeeMemberId(original.getEmployeeMemberId())
                    .contractType(template.getContractType())
                    .contentJson(contentJson)
                    .formSchemaSnapshot(template.getFormSchema())
                    .contractStatus(ContractStatus.SENT)
                    .contractNumber(contractNumber)
                    .sealImageUrl(sealImageUrl)
                    .employeeName(memberInfo.getName())
                    .employeeSabun(memberInfo.getSabun())
                    .organizationName(memberInfo.getOrganizationName())
                    .jobTitleName(memberInfo.getJobTitleName())
                    .previousContractId(original.getContractId())
                    .revision(original.getRevision() + 1)
                    .build();

            Contract savedContract = contractRepository.save(contract);

            ContractParty companyParty = ContractParty.builder()
                    .contract(savedContract)
                    .memberId(senderMemberId)
                    .partyRole(PartyRole.COMPANY)
                    .signStatus(SignStatus.SIGNED)
                    .build();
            companyParty.sign(null);

            ContractParty employeeParty = ContractParty.builder()
                    .contract(savedContract)
                    .memberId(original.getEmployeeMemberId())
                    .partyRole(PartyRole.EMPLOYEE)
                    .build();

            partyRepository.saveAll(List.of(companyParty, employeeParty));

            contractNotificationService.notifyContractSent(
                    original.getEmployeeMemberId(),
                    senderMemberId,
                    savedContract.getContractId(),
                    template.getTemplateName());
        }

        return ContractBatchResDto.fromEntity(savedBatch);
    }

    // 계약 이력 조회
    @Transactional(readOnly = true)
    public List<ContractResDto> findHistory(UUID companyId, UUID contractId) {
        Contract current = findContractWithPermission(companyId, contractId);
        return buildHistory(current);

    }

    // 직원용 이력 조회 (본인 확인)
    @Transactional(readOnly = true)
    public List<ContractResDto> findMyContractHistory(UUID companyId, UUID memberId, UUID contractId) {
        Contract current = findContractWithPermission(companyId, contractId);

        if (!current.getEmployeeMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인의 계약 이력만 조회할 수 있습니다.");
        }

        return buildHistory(current);
    }

    // 공통 이력 조회 로직
    private List<ContractResDto> buildHistory(Contract current) {
        List<ContractResDto> history = new ArrayList<>();

        Contract c = current;
        int maxDepth = 10;
        int depth = 0;
        while (c != null && depth < maxDepth) {
            List<ContractParty> parties = partyRepository.findByContractContractId(c.getContractId());
            history.add(ContractResDto.fromEntity(c, parties));

            if (c.getPreviousContractId() != null) {
                c = contractRepository.findByIdWithTemplate(c.getPreviousContractId()).orElse(null);
            } else {
                c = null;
            }
            depth++;
        }

        history.sort(Comparator.comparingInt(ContractResDto::getRevision));
        return history;
    }

    // 개별 서명 리마인드
    public void remindSign(UUID companyId, UUID senderMemberId, UUID contractId) {
        Contract contract = findContractWithPermission(companyId, contractId);

        if (contract.getContractStatus() != ContractStatus.SENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "발송 상태의 계약만 리마인드할 수 있습니다.");
        }

        contractNotificationService.notifySignReminder(
                contract.getEmployeeMemberId(),
                senderMemberId,
                contractId,
                contract.getContractTemplate().getTemplateName());
    }

    // 배치 미서명자 일괄 리마인드
    public int remindBatch(UUID companyId, UUID senderMemberId, UUID batchId) {
        List<Contract> unsignedContracts = contractRepository
                .findByContractBatchBatchIdAndContractStatusAndDelYn(
                        batchId, ContractStatus.SENT, "N");

        if (unsignedContracts.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "서명 대기 중인 계약이 없습니다.");
        }

        // 첫 건으로 companyId 검증
        if (!unsignedContracts.get(0).getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        for (Contract contract : unsignedContracts) {
            contractNotificationService.notifySignReminder(
                    contract.getEmployeeMemberId(),
                    senderMemberId,
                    contract.getContractId(),
                    contract.getContractTemplate().getTemplateName());
        }

        return unsignedContracts.size();
    }
}
