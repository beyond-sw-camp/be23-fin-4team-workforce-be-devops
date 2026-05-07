package com._team._team.approval.controller;

import com._team._team.approval.dto.reqdto.ApprovalActionReqDto;
import com._team._team.approval.dto.resdto.ApprovalRequestResDto;
import com._team._team.approval.service.ApprovalService;
import com._team._team.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    @Autowired
    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    // 결재 대기함 (내가 결재할 문서 + 대결 문서)
    @GetMapping("/pending")
    public ResponseEntity<?> findPending(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId) {
        List<ApprovalRequestResDto> result =
                approvalService.findPending(companyId, memberId, memberPositionId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "결재 대기 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 결재 완료함 (내가 결재한 문서 목록)
    @GetMapping("/acted")
    public ResponseEntity<?> findActed(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId) {
        List<ApprovalRequestResDto> result = approvalService.findActed(memberId, memberPositionId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "결재 완료 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 결재 예정함 (아직 내 차례가 아닌 문서)
    @GetMapping("/waiting")
    public ResponseEntity<?> findWaiting(
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId) {
        List<ApprovalRequestResDto> result = approvalService.findWaiting(memberPositionId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "결재 예정 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 결재함 전체 (대기 + 예정 통합)
    @GetMapping("/inbox")
    public ResponseEntity<?> findAllInbox(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId) {
        List<ApprovalRequestResDto> result =
                approvalService.findAllInbox(companyId, memberId, memberPositionId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "결재함 전체 조회 성공"),
                HttpStatus.OK
        );
    }

    // 승인 처리
    @PatchMapping("/{approvalId}/approve")
    public ResponseEntity<?> approve(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @PathVariable UUID approvalId,
            @RequestBody ApprovalActionReqDto reqDto) {
        ApprovalRequestResDto resDto =
                approvalService.approve(companyId, memberId, memberPositionId, approvalId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재가 승인되었습니다."),
                HttpStatus.OK
        );
    }

    @PatchMapping("/{approvalId}/reject")
    public ResponseEntity<?> reject(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @PathVariable UUID approvalId,
            @RequestBody ApprovalActionReqDto reqDto) {

        ApprovalRequestResDto result = approvalService.reject(companyId, memberId, memberPositionId, approvalId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(result, "결재가 반려되었습니다."),
                HttpStatus.OK
        );
    }


}
