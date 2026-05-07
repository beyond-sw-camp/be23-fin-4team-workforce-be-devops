package com._team._team.attendance.controller;


import com._team._team.attendance.domain.enums.LeaveApprovalStatus;
import com._team._team.attendance.dto.resDto.LeaveRequestResDto;
import com._team._team.attendance.service.LeaveRequestService;
import com._team._team.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


/**
 * 휴가 신청 관리자용 API
 */
@RestController
@RequestMapping("/attendance/leave-requests/admin")
public class LeaveRequestAdminController {

    private final LeaveRequestService leaveRequestService;

    @Autowired
    public LeaveRequestAdminController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    /**
     * 회사 내 상태별 휴가 신청 목록, 오래된 순
     */
    @GetMapping
    public ResponseEntity<?> findByStatus(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam LeaveApprovalStatus status) {

        List<LeaveRequestResDto> result = leaveRequestService
                .findByCompanyAndStatus(companyId, status).stream()
                .map(LeaveRequestResDto::fromEntity)
                .toList();

        return new ResponseEntity<>(
                ApiResponse.success(result, "휴가 신청 목록 조회 성공"),
                HttpStatus.OK);
    }
}