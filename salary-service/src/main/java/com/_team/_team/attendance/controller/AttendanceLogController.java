package com._team._team.attendance.controller;

import com._team._team.attendance.dto.resDto.WorkTimeSummaryResDto;
import com._team._team.attendance.service.*;
import com._team._team.attendance.dto.reqDto.AttendanceLogCreateReqDto;
import com._team._team.attendance.dto.resDto.AttendanceLogResDto;
import com._team._team.attendance.dto.resDto.DailyAttendanceResDto;
import com._team._team.dto.ApiResponse;
import com._team._team.dto.BusinessException;
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
 * 출퇴근 Controller
 *
 * [출퇴근 이벤트 — 사원 본인만 호출]
 * POST /attendance/clock-in       출근 (하루 1회)
 * POST /attendance/clock-out      퇴근 (하루 1회)
 *
 * [개인 조회 — 사원 본인]
 * GET  /attendance/daily/{date}   특정일 근태 요약 조회
 * GET  /attendance/logs/{date}    특정일 이벤트 로그 목록 조회
 * GET  /attendance/monthly        월간 근태 조회 (페이징)
 *
 * [관리자 조회 — HR 관리자]
 * GET  /attendance/company/daily     전 직원 특정일 근태 (페이징)
 * GET  /attendance/company/monthly   전 직원 월간 근태 (페이징)
 */
@RestController
@RequestMapping("/attendance")
public class AttendanceLogController {

    private final AttendanceLogService attendanceLogService;
    private final DailyAttendanceService dailyAttendanceService;
    private final LaborLawValidator laborLawValidator;

    @Autowired
    public AttendanceLogController(AttendanceLogService attendanceLogService,
                                   DailyAttendanceService dailyAttendanceService,
                                   LaborLawValidator laborLawValidator) {
        this.attendanceLogService = attendanceLogService;
        this.dailyAttendanceService = dailyAttendanceService;
        this.laborLawValidator = laborLawValidator;
    }

    // 출근 처리
    @PostMapping("/clock-in")
    public ResponseEntity<?> clockIn(
            @RequestHeader("X-User-CompanyId")UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestBody(required = false) AttendanceLogCreateReqDto reqDto){
        DailyAttendanceResDto resDto = attendanceLogService.clockIn(companyId, memberId,
                reqDto != null ? reqDto : new AttendanceLogCreateReqDto());
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "출근 처리되었습니다."),
                HttpStatus.CREATED
        );
    }

    // 퇴근 처리
    @PostMapping("/clock-out")
    public ResponseEntity<?> clockOut(
            @RequestHeader("X-User-CompanyId")UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestBody(required = false) AttendanceLogCreateReqDto reqDto) {

        DailyAttendanceResDto resDto = attendanceLogService.clockOut(companyId, memberId,
                reqDto != null ? reqDto : new AttendanceLogCreateReqDto());
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "퇴근 처리되었습니다."),
                HttpStatus.CREATED
        );
    }

    // 퇴근 취소 — 직원이 잘못 누른 경우 (오늘자, ClosureStatus=OPEN 한정)
    @DeleteMapping("/clock-out")
    public ResponseEntity<?> cancelClockOut(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId) {
        DailyAttendanceResDto resDto = attendanceLogService.cancelClockOut(companyId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "퇴근이 취소되었습니다."),
                HttpStatus.OK
        );
    }


    // ==================== 개인 조회 ====================

    // 개인 조회 - 사원 본인이 자기 근태 확인
    @GetMapping("/daily/{date}")
    public ResponseEntity<?> findDailyAttendance(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DailyAttendanceResDto resDto = dailyAttendanceService.findDailyAttendance(companyId, memberId, date);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "일별 근태 조회 성공"),
                HttpStatus.OK
        );
    }

    // 개인 조회 - 특정 날짜 출퇴근 로그 목록 조회
    @GetMapping("/logs/{date}")
    public ResponseEntity<?> findLogs(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<AttendanceLogResDto> resDtoList = attendanceLogService.findLogs(companyId, memberId, date);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "출퇴근 로그 조회 성공"),
                HttpStatus.OK
        );
    }

    // 개인 조회 - 월간 근태 조회 (페이징)
    @GetMapping("/monthly")
    public ResponseEntity<?> findMonthlyByMember(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 31) Pageable pageable) {
        Page<DailyAttendanceResDto> resDtoPage = dailyAttendanceService
                .findMonthlyByMember(companyId, memberId, from, to, pageable);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoPage, "월간 근태 조회 성공"),
                HttpStatus.OK
        );
    }

    // ==================== 관리자 조회 ====================

    /**
     * 회사 전체 특정일 근태 조회 - 관리자용 (페이징)
     */
    @GetMapping("/company/daily")
    public ResponseEntity<?> findDailyByCompany(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DailyAttendanceResDto> resDtoPage = dailyAttendanceService
                .findDailyByCompany(companyId, date, pageable);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoPage, "회사 일별 근태 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 회사 전체 월간 근태 조회 — 관리자용 (페이징)
     */
    @GetMapping("/company/monthly")
    public ResponseEntity<?> findMonthlyByCompany(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<DailyAttendanceResDto> resDtoPage = dailyAttendanceService
                .findMonthlyByCompany(companyId, from, to, pageable);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoPage, "회사 월간 근태 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 내 주간 근무시간 현황
     * 법정 52시간 대비 사용량 확인, 연장근무 신청 전 자가 체크용
     */
    @GetMapping("/my/work-time-summary")
    public ResponseEntity<?> getWeeklyWorkTimeSummary(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        WorkTimeSummaryResDto resDto = laborLawValidator
                .getWeeklySummary(memberId, companyId, targetDate);

        return new ResponseEntity<>(
                ApiResponse.success(resDto, "주간 근무시간 현황 조회 성공"),
                HttpStatus.OK);
    }
}
