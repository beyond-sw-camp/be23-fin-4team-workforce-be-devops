package com._team._team.salary.controller;

import com._team._team.salary.domain.MemberAllowance;
import com._team._team.salary.dto.reqdto.MemberAllowanceLinkApprovalReqDto;
import com._team._team.salary.dto.reqdto.MemberAllowanceRequestReqDto;
import com._team._team.salary.dto.resdto.MemberAllowanceResDto;
import com._team._team.salary.service.MemberAllowanceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 직원 본인의 수당 신청, 철회, 결재 연결, 이력 조회
@RestController
@RequestMapping("/salary/me/allowances")
public class MemberAllowanceController {

    private final MemberAllowanceService memberAllowanceService;

    @Autowired
    public MemberAllowanceController(MemberAllowanceService memberAllowanceService) {
        this.memberAllowanceService = memberAllowanceService;
    }

    // 본인 수당 변경 신청, PENDING 으로 생성
    @PostMapping
    public ResponseEntity<MemberAllowanceResDto> request(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody MemberAllowanceRequestReqDto reqDto) {

        MemberAllowance saved = memberAllowanceService.requestChange(
                memberId, companyId,
                reqDto.getSalaryItemTemplateId(),
                reqDto.getAmount(),
                reqDto.getEffectiveFrom(),
                reqDto.getReason());

        return ResponseEntity.ok(MemberAllowanceResDto.fromEntity(saved));
    }

    // approval-service 결재 생성 후 approvalRequestId 연결
    @PatchMapping("/{memberAllowanceId}/approval-link")
    public ResponseEntity<MemberAllowanceResDto> link(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID memberAllowanceId,
            @Valid @RequestBody MemberAllowanceLinkApprovalReqDto reqDto) {

        MemberAllowance updated = memberAllowanceService.linkApprovalRequest(
                memberAllowanceId, memberId, reqDto.getApprovalRequestId());

        return ResponseEntity.ok(MemberAllowanceResDto.fromEntity(updated));
    }

    // 본인 신청 철회, PENDING 만 가능
    @DeleteMapping("/{memberAllowanceId}")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID memberAllowanceId) {

        memberAllowanceService.cancel(memberAllowanceId, memberId);
        return ResponseEntity.noContent().build();
    }

    // 본인 수당 이력 전체 조회
    @GetMapping
    public ResponseEntity<List<MemberAllowanceResDto>> myHistory(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId) {

        List<MemberAllowanceResDto> result = memberAllowanceService
                .findMyHistory(memberId, companyId).stream()
                .map(MemberAllowanceResDto::fromEntity)
                .toList();

        return ResponseEntity.ok(result);
    }
}