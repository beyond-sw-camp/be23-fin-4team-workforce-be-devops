package com._team._team.attendance.controller;

import com._team._team.attendance.dto.reqDto.AttendanceCorrectionReqDto;
import com._team._team.attendance.dto.reqDto.AttendanceCorrectionRejectReqDto;
import com._team._team.attendance.dto.resDto.AttendanceCorrectionPendingResDto;
import com._team._team.attendance.dto.resDto.MissingAttendanceSuspectResDto;
import com._team._team.attendance.service.AttendanceCorrectionService;
import com._team._team.dto.ApiResponse;
import com._team._team.dto.BusinessException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 출퇴근 정정 신청·승인·반려 컨트롤러
 *
 * [직원 본인]
 * POST /attendance/correction                              정정 신청
 *
 * [관리자]
 * GET  /attendance/correction/pending                      검토 큐 조회
 * POST /attendance/correction/{daId}/approve               승인
 * POST /attendance/correction/{daId}/reject                반려
 */
@RestController
@RequestMapping("/attendance/correction")
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService attendanceCorrectionService;

    @Autowired
    public AttendanceCorrectionController(AttendanceCorrectionService attendanceCorrectionService) {
        this.attendanceCorrectionService = attendanceCorrectionService;
    }

    /** 직원: 누락 후보 일자 -  휴가/휴직 복귀 안전망 */
    @GetMapping("/my/missing-suspect")
    public ResponseEntity<?> findMyMissingSuspects(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId) {

        List<MissingAttendanceSuspectResDto> list =
                attendanceCorrectionService.findMissingSuspects(companyId, memberId);

        return new ResponseEntity<>(
                ApiResponse.success(list, "정정 후보 일자 조회 성공"),
                HttpStatus.OK);
    }

    /** 직원: 정정 신청 */
    @PostMapping
    public ResponseEntity<?> requestCorrection(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody AttendanceCorrectionReqDto reqDto) {

        UUID dailyAttendanceId = attendanceCorrectionService.requestCorrection(
                companyId, memberId, reqDto);

        return new ResponseEntity<>(
                ApiResponse.success(dailyAttendanceId, "정정 신청이 접수되었습니다. 관리자 검토 후 반영됩니다."),
                HttpStatus.CREATED);
    }

    /** 관리자: 정정 검토 큐 , UNDER_REVIEW 상태 DA 전체 */
    @GetMapping("/pending")
    public ResponseEntity<?> findPending(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader(value = "X-User-IsSystemAdmin", required = false) String isSystemAdmin) {

        assertAdmin(isSystemAdmin);

        List<AttendanceCorrectionPendingResDto> list =
                attendanceCorrectionService.findPending(companyId);

        return new ResponseEntity<>(
                ApiResponse.success(list, "정정 검토 대기 목록 조회 성공"),
                HttpStatus.OK);
    }

    /** 관리자: 승인 */
    @PostMapping("/{dailyAttendanceId}/approve")
    public ResponseEntity<?> approve(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID adminId,
            @RequestHeader(value = "X-User-IsSystemAdmin", required = false) String isSystemAdmin,
            @PathVariable UUID dailyAttendanceId) {

        assertAdmin(isSystemAdmin);

        attendanceCorrectionService.approve(companyId, dailyAttendanceId, adminId);

        return new ResponseEntity<>(
                ApiResponse.success(null, "정정 신청을 승인했습니다."),
                HttpStatus.OK);
    }

    /** 관리자: 반려 */
    @PostMapping("/{dailyAttendanceId}/reject")
    public ResponseEntity<?> reject(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID adminId,
            @RequestHeader(value = "X-User-IsSystemAdmin", required = false) String isSystemAdmin,
            @PathVariable UUID dailyAttendanceId,
            @Valid @RequestBody AttendanceCorrectionRejectReqDto reqDto) {

        assertAdmin(isSystemAdmin);

        attendanceCorrectionService.reject(
                companyId, dailyAttendanceId, adminId, reqDto.getRejectReason());

        return new ResponseEntity<>(
                ApiResponse.success(null, "정정 신청을 반려했습니다."),
                HttpStatus.OK);
    }

    private void assertAdmin(String isSystemAdminHeader) {
        if (!"YES".equals(isSystemAdminHeader)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "관리자만 사용할 수 있는 기능입니다.");
        }
    }
}
