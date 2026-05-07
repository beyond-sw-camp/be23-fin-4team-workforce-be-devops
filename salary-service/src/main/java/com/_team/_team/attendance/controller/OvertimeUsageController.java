package com._team._team.attendance.controller;

import com._team._team.attendance.dto.resDto.OvertimeUsageResDto;
import com._team._team.attendance.service.OvertimeUsageService;
import com._team._team.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 직원 월별 초과근무시간 누적 vs 회사 월 한도 현황 API
 */
@RestController
@RequestMapping("/attendance/overtime-usage")
public class OvertimeUsageController {

    private final OvertimeUsageService service;

    @Autowired
    public OvertimeUsageController(OvertimeUsageService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<OvertimeUsageResDto>>> getStatus(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam(value = "baseDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            @RequestParam(value = "mode", required = false, defaultValue = "MONTH") String mode) {

        LocalDate target = baseDate != null ? baseDate : LocalDate.now();
        OvertimeUsageService.PeriodMode periodMode = "WEEK".equalsIgnoreCase(mode)
                ? OvertimeUsageService.PeriodMode.WEEK
                : OvertimeUsageService.PeriodMode.MONTH;
        List<OvertimeUsageResDto> result = service.getStatus(companyId, target, periodMode);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(result, "조회 성공"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<OvertimeUsageResDto>> getMy(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam(value = "baseDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        LocalDate target = baseDate != null ? baseDate : LocalDate.now();
        OvertimeUsageResDto result = service.getMyStatus(companyId, memberId, target);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(result, "조회 성공"));
    }
}
