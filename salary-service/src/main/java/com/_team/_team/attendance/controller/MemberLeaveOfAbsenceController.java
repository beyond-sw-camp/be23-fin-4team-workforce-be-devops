package com._team._team.attendance.controller;

import com._team._team.attendance.domain.enums.LeaveOfAbsenceApprovalStatus;
import com._team._team.attendance.dto.reqDto.LeaveOfAbsenceLinkApprovalReqDto;
import com._team._team.attendance.dto.reqDto.LeaveOfAbsenceRequestReqDto;
import com._team._team.attendance.dto.resDto.LeaveOfAbsenceResDto;
import com._team._team.attendance.service.MemberLeaveOfAbsenceService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 휴직 직원용 + 관리자용 API
 * 직원: 신청, 철회, 결재 연결, 본인 이력
 * 관리자: 상태별 목록, 조기 복직 처리
 */
@RestController
@RequestMapping("/attendance/leave-of-absence")
public class MemberLeaveOfAbsenceController {

    private final MemberLeaveOfAbsenceService service;

    @Autowired
    public MemberLeaveOfAbsenceController(MemberLeaveOfAbsenceService service) {
        this.service = service;
    }

    // 직원용

    /** 본인 휴직 신청 */
    @PostMapping("/my")
    public ResponseEntity<?> submit(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody LeaveOfAbsenceRequestReqDto reqDto) {

        LeaveOfAbsenceResDto resDto = LeaveOfAbsenceResDto.fromEntity(
                service.submit(memberId, companyId, reqDto));
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "휴직 신청이 제출되었습니다."),
                HttpStatus.CREATED);
    }

    /** 본인 철회 */
    @DeleteMapping("/my/{leaveOfAbsenceId}")
    public ResponseEntity<?> cancel(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID leaveOfAbsenceId) {

        service.cancel(leaveOfAbsenceId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "휴직 신청이 취소되었습니다."),
                HttpStatus.OK);
    }

    /** 결재 ID 연결, approval-service 결재 생성 후 호출 */
    @PatchMapping("/my/{leaveOfAbsenceId}/approval-link")
    public ResponseEntity<?> linkApproval(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID leaveOfAbsenceId,
            @Valid @RequestBody LeaveOfAbsenceLinkApprovalReqDto reqDto) {

        LeaveOfAbsenceResDto resDto = LeaveOfAbsenceResDto.fromEntity(
                service.linkApprovalRequest(leaveOfAbsenceId, memberId,
                        reqDto.getApprovalRequestId()));
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재가 연결되었습니다."),
                HttpStatus.OK);
    }

    /** 내 휴직 이력 전체 */
    @GetMapping("/my")
    public ResponseEntity<?> findMyHistory(
            @RequestHeader("X-User-UUID") UUID memberId) {

        List<LeaveOfAbsenceResDto> result = service.findMyHistory(memberId).stream()
                .map(LeaveOfAbsenceResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(result, "내 휴직 이력 조회 성공"),
                HttpStatus.OK);
    }

    // 관리자용

    /** 상태별 목록, 결재 대기(REQUESTED), 휴직 중(ACTIVE), 종료(ENDED) 등 */
    @GetMapping("/admin")
    public ResponseEntity<?> findByStatus(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam LeaveOfAbsenceApprovalStatus status) {

        List<LeaveOfAbsenceResDto> result = service
                .findByCompanyAndStatus(companyId, status).stream()
                .map(LeaveOfAbsenceResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(result, "휴직 목록 조회 성공"),
                HttpStatus.OK);
    }

    /** 단건 조회, 회사 검증 포함 */
    @GetMapping("/{leaveOfAbsenceId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID leaveOfAbsenceId) {

        LeaveOfAbsenceResDto resDto = LeaveOfAbsenceResDto.fromEntity(
                service.findById(leaveOfAbsenceId, companyId));
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "휴직 조회 성공"),
                HttpStatus.OK);
    }

    /** 관리자 조기 복직 처리, actualEndDate 지정 */
    @PatchMapping("/admin/{leaveOfAbsenceId}/end")
    public ResponseEntity<?> endEarly(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID leaveOfAbsenceId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate actualEndDate) {

        service.endEarly(companyId, leaveOfAbsenceId, actualEndDate);
        return new ResponseEntity<>(
                ApiResponse.success(null, "휴직이 종료되었습니다."),
                HttpStatus.OK);
    }
}