package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryNegotiation;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.NegotiationStatus;
import com._team._team.salary.dto.reqdto.SalaryNegotiationBulkCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryNegotiationCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryNegotiationUpdateReqDto;
import com._team._team.salary.dto.resdto.SalaryNegotiationResDto;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.repository.SalaryNegotiationRepository;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 연봉 협상 서비스
 */
@Slf4j
@Service
@Transactional
public class SalaryNegotiationService {

    private final SalaryNegotiationRepository negotiationRepository;
    private final SalaryRepository salaryRepository;
    private final SalaryPolicyRepository salaryPolicyRepository;
    private final MemberFeignClient memberFeignClient;

    @Autowired
    public SalaryNegotiationService(SalaryNegotiationRepository negotiationRepository,
                                    SalaryRepository salaryRepository,
                                    SalaryPolicyRepository salaryPolicyRepository,
                                    MemberFeignClient memberFeignClient) {
        this.negotiationRepository = negotiationRepository;
        this.salaryRepository = salaryRepository;
        this.salaryPolicyRepository = salaryPolicyRepository;
        this.memberFeignClient = memberFeignClient;
    }

    /** 단건 협상 등록 */
    public SalaryNegotiationResDto create(UUID companyId, SalaryNegotiationCreateReqDto req) {
        Salary current = findActiveSalaryOrThrow(companyId, req.getMemberId(), req.getProposedEffectiveFrom());

        SalaryNegotiation neg = SalaryNegotiation.builder()
                .companyId(companyId)
                .memberId(req.getMemberId())
                .negotiationType(req.getNegotiationType())
                .currentBaseSalary(current.getBaseSalary())
                .currentJobGradeName(current.getJobGradeName())
                .currentJobTitleName(current.getJobTitleName())
                .proposedBaseSalary(req.getProposedBaseSalary())
                .proposedJobGradeName(req.getProposedJobGradeName())
                .proposedJobTitleName(req.getProposedJobTitleName())
                .proposedEffectiveFrom(req.getProposedEffectiveFrom())
                .reason(req.getReason())
                .build();
        neg.recalcChangeRate();

        SalaryNegotiation saved = negotiationRepository.save(neg);
        return SalaryNegotiationResDto.fromEntity(saved);
    }

    /** 일괄 등록 정기 시즌 같은 groupId 로 묶어서 N건 생성 */
    public List<SalaryNegotiationResDto> bulkCreate(UUID companyId, SalaryNegotiationBulkCreateReqDto req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "대상 직원이 없습니다.");
        }
        UUID groupId = UUID.randomUUID();
        List<SalaryNegotiation> toSave = new ArrayList<>(req.getItems().size());

        for (SalaryNegotiationBulkCreateReqDto.Item item : req.getItems()) {
            Salary current = findActiveSalaryOrThrow(
                    companyId, item.getMemberId(), req.getProposedEffectiveFrom());

            SalaryNegotiation neg = SalaryNegotiation.builder()
                    .companyId(companyId)
                    .memberId(item.getMemberId())
                    .negotiationType(req.getNegotiationType())
                    .groupId(groupId)
                    .groupName(req.getGroupName())
                    .currentBaseSalary(current.getBaseSalary())
                    .currentJobGradeName(current.getJobGradeName())
                    .currentJobTitleName(current.getJobTitleName())
                    .proposedBaseSalary(item.getProposedBaseSalary())
                    .proposedJobGradeName(item.getProposedJobGradeName())
                    .proposedJobTitleName(item.getProposedJobTitleName())
                    .proposedEffectiveFrom(req.getProposedEffectiveFrom())
                    .reason(item.getReason())
                    .build();
            neg.recalcChangeRate();
            toSave.add(neg);
        }
        List<SalaryNegotiation> saved = negotiationRepository.saveAll(toSave);
        return saved.stream().map(SalaryNegotiationResDto::fromEntity).toList();
    }

    // 수정
    public SalaryNegotiationResDto update(UUID companyId, UUID negotiationId, SalaryNegotiationUpdateReqDto req) {
        SalaryNegotiation neg = findOrThrow(companyId, negotiationId);
        neg.updateProposal(
                req.getProposedBaseSalary(),
                req.getProposedJobGradeName(),
                req.getProposedJobTitleName(),
                req.getProposedEffectiveFrom(),
                req.getReason());
        return SalaryNegotiationResDto.fromEntity(neg);
    }

    // ================== 상태 전이 ==================

    public SalaryNegotiationResDto submit(UUID companyId, UUID negotiationId) {
        SalaryNegotiation neg = findOrThrow(companyId, negotiationId);
        neg.submit();
        return SalaryNegotiationResDto.fromEntity(neg);
    }

    public SalaryNegotiationResDto approve(UUID companyId, UUID negotiationId, UUID approverId, String note) {
        SalaryNegotiation neg = findOrThrow(companyId, negotiationId);
        neg.approve(approverId, note);
        return SalaryNegotiationResDto.fromEntity(neg);
    }

    /**
     * 직원 본인이 협상 제안 수락
     */
    public SalaryNegotiationResDto acceptByEmployee(UUID companyId, UUID memberId, UUID negotiationId, String note) {
        SalaryNegotiation neg = findOrThrow(companyId, negotiationId);
        if (!neg.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 협상안만 응답할 수 있습니다.");
        }
        if (neg.getStatus() != NegotiationStatus.SUBMITTED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "검토 대기 상태의 협상만 수락할 수 있습니다.");
        }
        neg.approve(memberId, note);
        log.info("[NEGOTIATION-EMP-ACCEPT] negotiationId={} memberId={}", negotiationId, memberId);
        return SalaryNegotiationResDto.fromEntity(neg);
    }

    /**
     * 직원 본인이 협상 제안 거절
     */
    public SalaryNegotiationResDto rejectByEmployee(UUID companyId, UUID memberId, UUID negotiationId, String reason) {
        SalaryNegotiation neg = findOrThrow(companyId, negotiationId);
        if (!neg.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 협상안만 응답할 수 있습니다.");
        }
        if (neg.getStatus() != NegotiationStatus.SUBMITTED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "검토 대기 상태의 협상만 거절할 수 있습니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "거절 사유를 입력해주세요.");
        }
        neg.reject(memberId, reason);
        log.info("[NEGOTIATION-EMP-REJECT] negotiationId={} memberId={} reason={}",
                negotiationId, memberId, reason);
        return SalaryNegotiationResDto.fromEntity(neg);
    }

    /**
     * 적용 처리
     */
    public SalaryNegotiationResDto apply(UUID companyId, UUID negotiationId) {
        SalaryNegotiation neg = findOrThrow(companyId, negotiationId);
        if (neg.getStatus() != NegotiationStatus.APPROVED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "승인된 건만 적용할 수 있습니다.");
        }

        // 활성 정책 (적용 시작일 시점)
        SalaryPolicy policy = salaryPolicyRepository
                .findActivePolicies(companyId, neg.getProposedEffectiveFrom())
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "활성 급여 정책이 없습니다."));

        // 이전 활성 Salary 조회 - 마감 + 부가 항목(직급/직책/부양가족) fallback 용도
        Salary prevActive = salaryRepository
                .findActiveSalary(neg.getMemberId(), companyId, neg.getProposedEffectiveFrom())
                .orElse(null);
        if (prevActive != null) {
            prevActive.closeEffectivePeriod(neg.getProposedEffectiveFrom().minusDays(1));
        }

        // 부양가족수 - 이전 행 값 보존 (협상 대상 필드 아님)
        Integer prevDep = salaryRepository
                .findByMemberIdAndCompanyId(neg.getMemberId(), companyId)
                .stream()
                .map(Salary::getDependentCount)
                .filter(d -> d != null)
                .findFirst()
                .orElse(1);

        // 직급 / 직책 - 협상에서 명시 제안 안 됐으면 (단순 인상 케이스) 이전 Salary 값 그대로 승계
        String jobGradeName = neg.getProposedJobGradeName() != null
                ? neg.getProposedJobGradeName()
                : (prevActive != null ? prevActive.getJobGradeName() : null);
        String jobTitleName = neg.getProposedJobTitleName() != null
                ? neg.getProposedJobTitleName()
                : (prevActive != null ? prevActive.getJobTitleName() : null);

        Salary newSalary = Salary.builder()
                .memberId(neg.getMemberId())
                .companyId(companyId)
                .salaryPolicyId(policy.getSalaryPolicyId())
                .baseSalary(neg.getProposedBaseSalary())
                .jobGradeName(jobGradeName)
                .jobTitleName(jobTitleName)
                .effectiveFrom(neg.getProposedEffectiveFrom())
                .effectiveTo(null)
                .dependentCount(prevDep)
                .build();

        Salary saved = salaryRepository.save(newSalary);
        neg.apply(saved.getSalaryId());

        log.info("[NEGOTIATION-APPLY] negotiationId={} memberId={} newSalaryId={} effectiveFrom={}",
                negotiationId, neg.getMemberId(), saved.getSalaryId(), neg.getProposedEffectiveFrom());

        return SalaryNegotiationResDto.fromEntity(neg);
    }

    public void delete(UUID companyId, UUID negotiationId) {
        SalaryNegotiation neg = findOrThrow(companyId, negotiationId);
        if (neg.getStatus() == NegotiationStatus.APPLIED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "적용된 협상은 삭제할 수 없습니다.");
        }
        neg.softDelete();
    }

    // ================== 조회 ==================

    @Transactional(readOnly = true)
    public List<SalaryNegotiationResDto> listByCompany(UUID companyId) {
        List<SalaryNegotiation> list = negotiationRepository.findByCompanyId(companyId);
        if (list.isEmpty()) return Collections.emptyList();

        Map<UUID, MemberResDto> memberMap = fetchMemberMap(companyId);
        return list.stream()
                .map(n -> mapWithMember(n, memberMap))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SalaryNegotiationResDto> listByGroup(UUID companyId, UUID groupId) {
        List<SalaryNegotiation> list = negotiationRepository.findByCompanyIdAndGroupId(companyId, groupId);
        if (list.isEmpty()) return Collections.emptyList();

        Map<UUID, MemberResDto> memberMap = fetchMemberMap(companyId);
        return list.stream()
                .map(n -> mapWithMember(n, memberMap))
                .toList();
    }

    @Transactional(readOnly = true)
    public SalaryNegotiationResDto findById(UUID companyId, UUID negotiationId) {
        SalaryNegotiation neg = findOrThrow(companyId, negotiationId);
        Map<UUID, MemberResDto> memberMap = fetchMemberMap(companyId);
        return mapWithMember(neg, memberMap);
    }

    @Transactional(readOnly = true)
    public List<SalaryNegotiationResDto> listMine(UUID companyId, UUID memberId) {
        return negotiationRepository.findByCompanyIdAndMemberId(companyId, memberId).stream()
                .map(SalaryNegotiationResDto::fromEntity)
                .toList();
    }

    // ================== 내부 헬퍼 ==================

    private SalaryNegotiation findOrThrow(UUID companyId, UUID negotiationId) {
        return negotiationRepository
                .findByNegotiationIdAndCompanyIdAndDelYn(negotiationId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "협상 건을 찾을 수 없습니다."));
    }

    private Salary findActiveSalaryOrThrow(UUID companyId, UUID memberId, LocalDate date) {
        return salaryRepository.findActiveSalary(memberId, companyId, date)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "직원의 활성 급여가 없습니다 — 먼저 Salary 등록 필요"));
    }

    private Map<UUID, MemberResDto> fetchMemberMap(UUID companyId) {
        try {
            var res = memberFeignClient.getMembersByCompany(companyId);
            if (res == null || res.getData() == null) return Collections.emptyMap();
            return res.getData().stream()
                    .collect(Collectors.toMap(MemberResDto::getMemberId, m -> m, (a, b) -> a));
        } catch (Exception e) {
            log.warn("[NEGOTIATION-LIST] 직원 조회 실패 companyId={} 빈 Map 반환", companyId, e);
            return Collections.emptyMap();
        }
    }

    private SalaryNegotiationResDto mapWithMember(SalaryNegotiation n, Map<UUID, MemberResDto> memberMap) {
        MemberResDto m = memberMap.get(n.getMemberId());
        return SalaryNegotiationResDto.fromEntity(
                n,
                m != null ? m.getSabun() : null,
                m != null ? m.getName() : null,
                m != null ? m.getOrganizationName() : null);
    }
}
