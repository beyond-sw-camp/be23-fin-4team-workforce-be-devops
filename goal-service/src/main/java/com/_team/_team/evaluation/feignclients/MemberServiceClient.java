package com._team._team.evaluation.feignclients;

import com._team._team.evaluation.feignclients.dto.MemberMinimalProfileDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * member-service 호출용 Feign 클라이언트.
 * 평가 결과 DTO 에 대상자/평가자 이름·소속·프로필을 채워넣기 위해 사용한다.
 */
@FeignClient(name = "member-service", contextId = "goalServiceMemberClient")
public interface MemberServiceClient {

    /**
     * memberId 목록 → 최소 프로필(name/department/positionName/profileUrl) 배치 조회.
     */
    @GetMapping("/member/internal/profiles")
    Map<UUID, MemberMinimalProfileDto> getProfilesByIds(@RequestParam("ids") List<UUID> ids);

    /**
     * 특정 대상자 기준 평가자 후보 조회 — 조직 + 직급 기반.
     * evalType = SELF | DOWNWARD | UPWARD | PEER
     */
    @GetMapping("/member/internal/candidates-for-evaluator")
    List<UUID> getCandidatesForEvaluator(
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam("targetMemberId") UUID targetMemberId,
            @RequestParam("evalType") String evalType);
}
