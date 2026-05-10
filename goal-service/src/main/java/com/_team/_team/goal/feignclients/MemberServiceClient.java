package com._team._team.goal.feignclients;

import com._team._team.dto.ApiResponse;
import com._team._team.goal.feignclients.dto.MemberOrgContextDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "member-service", contextId = "goalMemberServiceClient")
public interface MemberServiceClient {

    @GetMapping("/member/internal/candidates-for-evaluator")
    List<UUID> getCandidatesForEvaluator(
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam("targetMemberId") UUID targetMemberId,
            @RequestParam("evalType") String evalType);

    @GetMapping("/member/internal/{memberId}/org-context")
    MemberOrgContextDto getOrgContext(@PathVariable("memberId") UUID memberId);

    @GetMapping("/member/internal/org-contexts")
    Map<UUID, MemberOrgContextDto> getOrgContexts(@RequestParam("ids") List<UUID> memberIds);

    @GetMapping("/member/internal/organizations/{organizationId}/member-ids")
    List<UUID> getMemberIdsByOrganization(@PathVariable("organizationId") UUID organizationId);

    // 회사 인사 시스템 관리자 memberId 목록 - 평가 결과 공개 시 성과급 발행 알림 대상
    @GetMapping("/member/internal/admin-ids-by-company")
    ApiResponse<List<UUID>> getAdminMemberIdsByCompany(@RequestParam("companyId") UUID companyId);

    default List<UUID> findManagedMemberIds(UUID companyId, UUID managerMemberId) {
        try {
            return getCandidatesForEvaluator(companyId.toString(), managerMemberId, "UPWARD");
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    default UUID findDirectManagerId(UUID companyId, UUID memberId) {
        try {
            List<UUID> candidates = getCandidatesForEvaluator(companyId.toString(), memberId, "DOWNWARD");
            return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
        } catch (Exception ignore) {
            return null;
        }
    }

    default UUID findOrganizationId(UUID companyId, UUID memberId) {
        try {
            MemberOrgContextDto context = getOrgContext(memberId);
            return context != null ? context.getOrganizationId() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    default List<UUID> findMemberIdsByOrgId(UUID companyId, UUID orgId) {
        try {
            return getMemberIdsByOrganization(orgId);
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    // 회사 인사 시스템 관리자 memberId 목록
    default List<UUID> findAdminMemberIds(UUID companyId) {
        try {
            ApiResponse<List<UUID>> res = getAdminMemberIdsByCompany(companyId);
            if (res == null || res.getData() == null) return Collections.emptyList();
            return res.getData();
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

}
