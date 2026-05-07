package com._team._team.attendance.controller;

import com._team._team.attendance.dto.reqDto.LeaveRequestLinkApprovalReqDto;
import com._team._team.attendance.dto.reqDto.LeaveRequestSubmitReqDto;
import com._team._team.attendance.dto.resDto.LeaveRequestResDto;
import com._team._team.attendance.service.LeaveRequestService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 휴가 신청 직원용 API
 */
@RestController
@RequestMapping("/attendance/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @Autowired
    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    /**
     * 휴가 신청 제출
     */
    @PostMapping("/my")
    public ResponseEntity<?> submit(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody LeaveRequestSubmitReqDto reqDto) {

        LeaveRequestResDto resDto = LeaveRequestResDto.fromEntity(
                leaveRequestService.submit(memberId, companyId, reqDto));
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "휴가 신청이 제출되었습니다."),
                HttpStatus.CREATED);
    }

    /**
     * 본인 철회
     */
    @DeleteMapping("/my/{leaveRequestId}")
    public ResponseEntity<?> cancel(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID leaveRequestId) {

        leaveRequestService.cancel(leaveRequestId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "휴가 신청이 취소되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 결재 ID 연결
     */
    @PatchMapping("/my/{leaveRequestId}/approval-link")
    public ResponseEntity<?> linkApproval(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID leaveRequestId,
            @Valid @RequestBody LeaveRequestLinkApprovalReqDto reqDto) {

        LeaveRequestResDto resDto = LeaveRequestResDto.fromEntity(
                leaveRequestService.linkApprovalRequest(
                        leaveRequestId, memberId, reqDto.getApprovalRequestId()));
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재가 연결되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 내 신청 이력, 최근 순
     */
    @GetMapping("/my")
    public ResponseEntity<?> findMyHistory(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<LeaveRequestResDto> result = leaveRequestService
                .findMyHistory(memberId, companyId, pageable)
                .map(LeaveRequestResDto::fromEntity);

        return new ResponseEntity<>(
                ApiResponse.success(result, "휴가 신청 이력 조회 성공"),
                HttpStatus.OK);
    }

    /**
     * 내 특정일 신청 목록 (같은 날 여러 건 가능)
     */
    @GetMapping("/my/by-date")
    public ResponseEntity<?> findMyByDate(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<LeaveRequestResDto> result = leaveRequestService
                .findMyByDate(memberId, date).stream()
                .map(LeaveRequestResDto::fromEntity)
                .toList();

        return new ResponseEntity<>(
                ApiResponse.success(result, "일자별 휴가 신청 조회 성공"),
                HttpStatus.OK);
    }

    /**
     * 단건 조회, 본인 또는 관리자
     */
    @GetMapping("/{leaveRequestId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID leaveRequestId) {

        LeaveRequestResDto resDto = LeaveRequestResDto.fromEntity(
                leaveRequestService.findById(leaveRequestId, companyId));
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "휴가 신청 조회 성공"),
                HttpStatus.OK);
    }
}