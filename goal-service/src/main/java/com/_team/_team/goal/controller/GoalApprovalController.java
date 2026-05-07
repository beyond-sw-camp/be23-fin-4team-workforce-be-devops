package com._team._team.goal.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.goal.dto.reqdto.BundleApproveReqDto;
import com._team._team.goal.dto.reqdto.BundleRejectReqDto;
import com._team._team.goal.dto.reqdto.SubmitCycleReqDto;
import com._team._team.goal.dto.resdto.BundleResDto;
import com._team._team.goal.service.GoalApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * GoalApprovalController — Phase 3
 *
 *  엔드포인트:
 *    POST   /goal/approval/cycles/{cycleKey}/submit       — 일괄 승인 요청
 *    POST   /goal/approval-bundles/{bundleId}/approve     — 승인
 *    POST   /goal/approval-bundles/{bundleId}/reject      — 반려
 *    POST   /goal/approval-bundles/{bundleId}/withdraw    — 회수
 *    GET    /goal/approval-bundles/{bundleId}             — 단건
 *    GET    /goal/approval-bundles/me/requested           — 내가 요청한
 *    GET    /goal/approval-bundles/me/queue               — 내가 처리해야 할 (PENDING)
 */
@RestController
public class GoalApprovalController {

    private final GoalApprovalService approvalService;

    public GoalApprovalController(GoalApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    // -----------------------------------------------------------------
    //  Submit
    // -----------------------------------------------------------------
    @PostMapping("/goal/approval/cycles/{cycleKey}/submit")
    public ApiResponse<BundleResDto> submitCycle(
            @PathVariable String cycleKey,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestBody @Valid SubmitCycleReqDto dto) {
        return ApiResponse.success(
                approvalService.submitCycle(
                        UUID.fromString(memberId),
                        UUID.fromString(companyId),
                        cycleKey,
                        dto),
                "승인 요청이 등록되었습니다."
        );
    }

    // -----------------------------------------------------------------
    //  Decision
    // -----------------------------------------------------------------
    @PostMapping("/goal/approval-bundles/{bundleId}/approve")
    public ApiResponse<BundleResDto> approve(
            @PathVariable UUID bundleId,
            @RequestHeader("X-User-UUID") String approverId,
            @RequestBody(required = false) @Valid BundleApproveReqDto dto) {
        BundleApproveReqDto safe = dto != null ? dto : BundleApproveReqDto.builder().build();
        return ApiResponse.success(
                approvalService.approve(bundleId, UUID.fromString(approverId), safe),
                "승인되었습니다."
        );
    }

    @PostMapping("/goal/approval-bundles/{bundleId}/reject")
    public ApiResponse<BundleResDto> reject(
            @PathVariable UUID bundleId,
            @RequestHeader("X-User-UUID") String approverId,
            @RequestBody @Valid BundleRejectReqDto dto) {
        return ApiResponse.success(
                approvalService.reject(bundleId, UUID.fromString(approverId), dto),
                "반려되었습니다."
        );
    }

    @PostMapping("/goal/approval-bundles/{bundleId}/withdraw")
    public ApiResponse<BundleResDto> withdraw(
            @PathVariable UUID bundleId,
            @RequestHeader("X-User-UUID") String memberId) {
        return ApiResponse.success(
                approvalService.withdraw(bundleId, UUID.fromString(memberId)),
                "회수되었습니다."
        );
    }

    // -----------------------------------------------------------------
    //  조회
    // -----------------------------------------------------------------
    @GetMapping("/goal/approval-bundles/{bundleId}")
    public ApiResponse<BundleResDto> get(
            @PathVariable UUID bundleId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                approvalService.get(bundleId, UUID.fromString(memberId), UUID.fromString(companyId)),
                "bundle 조회 성공"
        );
    }

    @GetMapping("/goal/approval-bundles/me/requested")
    public ApiResponse<List<BundleResDto>> myRequested(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                approvalService.listMyRequested(UUID.fromString(memberId), UUID.fromString(companyId)),
                "내가 요청한 승인 목록"
        );
    }

    @GetMapping("/goal/approval-bundles/me/queue")
    public ApiResponse<List<BundleResDto>> myQueue(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                approvalService.listMyApprovalQueue(UUID.fromString(memberId), UUID.fromString(companyId)),
                "내가 처리해야 할 승인 목록"
        );
    }
}
