package com._team._team.goal.feignclients;

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

}
