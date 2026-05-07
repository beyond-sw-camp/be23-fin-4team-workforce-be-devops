package com._team._team.attendance.controller;

import com._team._team.attendance.dto.reqDto.OvertimeRequestCreateReqDto;
import com._team._team.attendance.dto.resDto.OvertimeRequestResDto;
import com._team._team.attendance.service.OvertimeRequestService;
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
 * 연장근로 신청 API
 */
@RestController
@RequestMapping("/attendance/overtime-requests")
public class OvertimeRequestController {

    private final OvertimeRequestService overtimeRequestService;

    @Autowired
    public OvertimeRequestController(OvertimeRequestService overtimeRequestService) {
        this.overtimeRequestService = overtimeRequestService;
    }
    /**
     * 신청 제출
     */
    @PostMapping("/my")
    public ResponseEntity<?> submit(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody OvertimeRequestCreateReqDto reqDto) {

        OvertimeRequestResDto resDto =
                overtimeRequestService.submit(companyId, memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "연장근로 신청이 제출되었습니다."),
                HttpStatus.CREATED);
    }

    /**
     * 본인 철회
     */
    @DeleteMapping("/my/{overtimeRequestId}")
    public ResponseEntity<?> cancel(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID overtimeRequestId) {

        overtimeRequestService.cancel(overtimeRequestId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "신청이 취소되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 결재 ID 연결
     */
    @PutMapping("/my/{overtimeRequestId}/approval-link")
    public ResponseEntity<?> linkApproval(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID overtimeRequestId,
            @RequestParam UUID approvalRequestId) {

        OvertimeRequestResDto resDto = overtimeRequestService.linkApprovalRequest(
                overtimeRequestId, memberId, approvalRequestId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재가 연결되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 내 신청 이력
     */
    @GetMapping("/my")
    public ResponseEntity<?> findMyHistory(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<OvertimeRequestResDto> resDto =
                overtimeRequestService.findMyHistory(memberId, pageable);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "신청 이력 조회 성공"),
                HttpStatus.OK);
    }

    /**
     * 내 특정 일자 신청 목록
     */
    @GetMapping("/my/by-date")
    public ResponseEntity<?> findMyByDate(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<OvertimeRequestResDto> resDto =
                overtimeRequestService.findMyByDate(memberId, date);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "일자별 조회 성공"),
                HttpStatus.OK);
    }

    /**
     * 신청 단건 조회
     */
    @GetMapping("/{overtimeRequestId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID overtimeRequestId) {

        OvertimeRequestResDto resDto =
                overtimeRequestService.findById(overtimeRequestId, companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "신청 조회 성공"),
                HttpStatus.OK);
    }
}