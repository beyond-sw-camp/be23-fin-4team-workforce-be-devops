package com._team._team.salary.feignClients;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.feignClients.dto.CompanyInfoResDto;
import com._team._team.salary.feignClients.dto.MemberResDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "member-service")
public interface MemberFeignClient {

    @GetMapping("/member/{targetMemberId}")
    ApiResponse<MemberResDto> getMember(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @PathVariable UUID targetMemberId);

    @GetMapping("/member/list")
    ApiResponse<List<MemberResDto>> getMemberList(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId);

    /**
     * 회사 소속 재직 사원 전체 조회
     * - 배치 스케줄러에서 호출
     */
    @GetMapping("/member/internal/by-company")
    ApiResponse<List<MemberResDto>> getMembersByCompany(
            @RequestHeader("X-User-CompanyId") UUID companyId);

    /**
     * 회사 도메인으로 회사 단건 조회
     * DemoSalarySeedRunner 등 시드 작업에서 사용
     */
    @GetMapping("/company/internal/by-domain/{domain}")
    ApiResponse<CompanyInfoResDto> getCompanyByDomain(@PathVariable("domain") String domain);

    /**
     * 회사 시스템 관리자 (isSystemAdminYn=YES) 조회 - 알림 발송 대상
     */
    @GetMapping("/member/internal/admins-by-company")
    ApiResponse<List<MemberResDto>> getAdminsByCompany(@RequestParam("companyId") UUID companyId);

    @GetMapping("/member/internal/{memberId}/info")
    MemberResDto getMemberInfoInternal(@PathVariable("memberId") UUID memberId);

    /**
     * BatchScheduler 시드용
     */
    @GetMapping("/company/internal/all-ids")
    ApiResponse<List<UUID>> getAllCompanyIds();

}
