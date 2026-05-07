package com._team._team.approval.service;

import com._team._team.approval.domain.ApprovalDocument;
import com._team._team.approval.domain.ApprovalPolicyLine;
import com._team._team.approval.dto.reqdto.ApprovalPolicyLineCreateReqDto;
import com._team._team.approval.dto.resdto.ApprovalPolicyLineResDto;
import com._team._team.approval.dto.resdto.PolicyLineCandidateResDto;
import com._team._team.approval.feignclients.MemberServiceClient;
import com._team._team.approval.feignclients.dto.MemberPositionResDto;
import com._team._team.approval.feignclients.dto.OrganizationResDto;
import com._team._team.approval.repository.ApprovalDocumentRepository;
import com._team._team.approval.repository.ApprovalPolicyLineRepository;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ApprovalPolicyLineService {
    private final ApprovalPolicyLineRepository policyLineRepository;
    private final ApprovalDocumentRepository approvalDocumentRepository;
    private final MemberServiceClient memberServiceClient;

    @Autowired
    public ApprovalPolicyLineService(ApprovalPolicyLineRepository policyLineRepository, ApprovalDocumentRepository approvalDocumentRepository, MemberServiceClient memberServiceClient) {
        this.policyLineRepository = policyLineRepository;
        this.approvalDocumentRepository = approvalDocumentRepository;
        this.memberServiceClient = memberServiceClient;
    }

//    결재라인 정책 일괄 저장(전체 교체)
    public List<ApprovalPolicyLineResDto> savePolicyLines(UUID companyId, ApprovalPolicyLineCreateReqDto reqDto){
        ApprovalDocument document = approvalDocumentRepository.findById(reqDto.getDocumentId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        // stepOrder 연속성 검증 (1, 2, 3...)
        List<Integer> stepOrders = reqDto.getPolicyLines().stream()
                .map(ApprovalPolicyLineCreateReqDto.PolicyLineItem::getStepOrder)
                .sorted()
                .toList();

        for (int i = 0; i < stepOrders.size(); i++) {
            if (stepOrders.get(i) != i + 1) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "결재 순서는 1부터 연속되어야 합니다.");
            }
        }

        // stepOrder 중복 검증
        if (stepOrders.size() != stepOrders.stream().distinct().count()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "동일한 결재 순서를 중복 지정할 수 없습니다.");
        }


        // 기존 라인 전체 삭제 후 새로 저장
        List<ApprovalPolicyLine> existingLines =
                policyLineRepository.findByDocumentId(reqDto.getDocumentId());
        existingLines.forEach(ApprovalPolicyLine::approvalPolicyLineDelete);

        // 새 라인 저장
        List<ApprovalPolicyLine> newLines = reqDto.getPolicyLines().stream()
                .map(item -> ApprovalPolicyLine.builder()
                        .approvalDocument(document)
                        .jobTitleId(item.getJobTitleId())
                        .stepOrder(item.getStepOrder())
                        .organizationId(item.getOrganizationId())
                        .build())
                .toList();

        List<ApprovalPolicyLine> saved = policyLineRepository.saveAll(newLines);

        return saved.stream()
                .map(ApprovalPolicyLineResDto::fromEntity)
                .toList();
    }



    // 특정 조직에서 직책으로 검색 (본인 제외)
    private List<MemberPositionResDto> searchInOrganization(
            UUID organizationId, UUID jobTitleId, UUID requesterMemberPositionId) {

        List<MemberPositionResDto> found;
        try {
            found = memberServiceClient.searchByJobTitle(organizationId, jobTitleId);
        } catch (Exception e) {
            return new ArrayList<>(); // 조회 실패 시 빈 후보 목록 반환
        }

        if (found == null || found.isEmpty()) {
            return new ArrayList<>();
        }

        return found.stream()
                .filter(c -> !c.getMemberPositionId().equals(requesterMemberPositionId))
                .toList();
    }

    // 양식별 결재라인 정책 조회
    @Transactional(readOnly = true)
    public List<ApprovalPolicyLineResDto> findByDocumentId(UUID companyId, UUID documentId) {
        ApprovalDocument document = approvalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        return policyLineRepository.findByDocumentId(documentId).stream()
                .map(ApprovalPolicyLineResDto::fromEntity)
                .toList();
    }

    // 양식별 결재라인 정책 전체 삭제
    public void deletePolicyLines(UUID companyId, UUID documentId) {
        ApprovalDocument document = approvalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        List<ApprovalPolicyLine> existingLines =
                policyLineRepository.findByDocumentId(documentId);
        existingLines.forEach(ApprovalPolicyLine::approvalPolicyLineDelete);
    }

    // 양식별 후보 결재자 조회
    @Transactional(readOnly = true)
    public List<PolicyLineCandidateResDto> findCandidatesByDocumentId(
            UUID companyId, UUID documentId, UUID memberPositionId) {

        ApprovalDocument document = approvalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        // 1. 요청자 MemberPosition 조회
        MemberPositionResDto requester;
        try {
            requester = memberServiceClient.getMemberPosition(memberPositionId);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "인사 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        if (requester == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "요청자 정보를 찾을 수 없습니다.");
        }

        // 2. 정책라인 조회 (stepOrder 순)
        List<ApprovalPolicyLine> policyLines =
                policyLineRepository.findByDocumentId(documentId);

        // 3. 요청자의 직책이 정책라인에 있으면 그 다음 stepOrder부터 필터
        Integer requesterStepOrder = null;
        for (ApprovalPolicyLine line : policyLines) {
            if (line.getJobTitleId().equals(requester.getJobTitleId())
                    && line.getOrganizationId() == null) {
                requesterStepOrder = line.getStepOrder();
                break;
            }
        }

        List<PolicyLineCandidateResDto> result = new ArrayList<>();

        for (ApprovalPolicyLine policyLine : policyLines) {
            // 본인 직급도 포함
            if (requesterStepOrder != null && policyLine.getStepOrder() < requesterStepOrder) {
                continue;
            }

            List<MemberPositionResDto> candidates;

            if (policyLine.getOrganizationId() != null) {
                // organizationId 지정됨 → 해당 조직에서 직책 검색
                candidates = searchInOrganization(
                        policyLine.getOrganizationId(),
                        policyLine.getJobTitleId(),
                        memberPositionId);
            } else {
                // organizationId 없음 → 요청자 조직부터 상위로 올라가며 검색
                candidates = searchUpOrganizationTree(
                        requester.getOrganizationId(),
                        policyLine.getJobTitleId(),
                        memberPositionId);
            }

            result.add(PolicyLineCandidateResDto.builder()
                    .policyLineId(policyLine.getPolicyLineId())
                    .documentId(documentId)
                    .jobTitleId(policyLine.getJobTitleId())
                    .stepOrder(policyLine.getStepOrder())
                    .organizationId(policyLine.getOrganizationId())
                    .candidates(candidates)
                    .build());
        }

        return result;
    }

    // 요청자 소속 조직부터 상위로 올라가며 검색 (본인 제외 후 후보 없으면 상위로)
    private List<MemberPositionResDto> searchUpOrganizationTree(
            UUID startOrganizationId, UUID jobTitleId, UUID requesterMemberPositionId) {

        UUID currentOrgId = startOrganizationId;
        int maxDepth = 10; // 무한 루프 방지

        for (int i = 0; i < maxDepth; i++) {
            List<MemberPositionResDto> candidates =
                    searchInOrganization(currentOrgId, jobTitleId, requesterMemberPositionId);

            if (!candidates.isEmpty()) {
                return candidates;
            }

            OrganizationResDto org;
            try {
                org = memberServiceClient.getOrganization(currentOrgId);
            } catch (Exception e) {
                return new ArrayList<>(); // 조직 조회 실패 시 현재까지 결과로 종료
            }

            if (org == null || org.getParentId() == null) {
                return new ArrayList<>();
            }

            currentOrgId = org.getParentId();
        }

        return new ArrayList<>();
    }

}
