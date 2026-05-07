package com._team._team.approval.controller;

import com._team._team.approval.dto.resdto.ApprovalRequestResDto;
import com._team._team.approval.service.ApprovalViewerService;
import com._team._team.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/viewers")
public class ApprovalViewerController {

    private final ApprovalViewerService approvalViewerService;

    @Autowired
    public ApprovalViewerController(ApprovalViewerService approvalViewerService) {
        this.approvalViewerService = approvalViewerService;
    }

    @GetMapping("/cc")
    public ResponseEntity<?> findCcList(
            @RequestHeader("X-User-UUID") UUID memberId) {

        List<ApprovalRequestResDto> result = approvalViewerService.findCcList(memberId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "참조 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    @GetMapping("/circulation")
    public ResponseEntity<?> findCirculationList(
            @RequestHeader("X-User-UUID") UUID memberId) {

        List<ApprovalRequestResDto> result = approvalViewerService.findCirculationList(memberId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "공람 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    @PatchMapping("/{viewerId}/read")
    public ResponseEntity<?> markAsRead(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID viewerId) {

        approvalViewerService.markAsRead(memberId, viewerId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "읽음 처리 완료"),
                HttpStatus.OK
        );
    }
}
