package com._team._team.approval.feignclients;

import com._team._team.approval.feignclients.dto.MemberContractInfoResDto;
import com._team._team.approval.feignclients.dto.MemberPositionResDto;
import com._team._team.approval.feignclients.dto.OrganizationResDto;
import com._team._team.approval.feignclients.dto.SignatureResDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "member-service", contextId = "approvalServiceClient", url = "${feign.url.member-service:}") // k8s 배포 시 유레카 x
public interface MemberServiceClient {

    @GetMapping("/member/position/internal/{memberPositionId}")
    MemberPositionResDto getMemberPosition(
            @PathVariable("memberPositionId") UUID memberPositionId);

    @GetMapping("/member/position/internal/search/by-job-title")
    List<MemberPositionResDto> searchByJobTitle(
            @RequestParam("organizationId") UUID organizationId,
            @RequestParam("jobTitleId") UUID jobTitleId);

    @GetMapping("/organization/internal/{organizationId}")
    OrganizationResDto getOrganization(
            @PathVariable("organizationId") UUID organizationId);

    @GetMapping("/organization/internal/{organizationId}/descendants")
    List<UUID> getOrganizationDescendantIds(
            @PathVariable("organizationId") UUID organizationId);

    @GetMapping("/member/internal/{memberId}/signature")
    SignatureResDto getSignatureUrl(@PathVariable("memberId") UUID memberId);

    @GetMapping("/members/active")
    List<UUID> findActiveMemberIds(@RequestParam("companyId") UUID companyId);

    @GetMapping("/member/internal/{memberId}/info")
    MemberContractInfoResDto getMemberContractInfo(@PathVariable("memberId") UUID memberId);

    @GetMapping("/company/internal/{companyId}/seal")
    String getCompanySealImageUrl(@PathVariable("companyId") UUID companyId);
}