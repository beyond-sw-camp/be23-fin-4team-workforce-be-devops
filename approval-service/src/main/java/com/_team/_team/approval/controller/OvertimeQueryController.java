package com._team._team.approval.controller;

import com._team._team.approval.service.OvertimeQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/approval/overtime")
public class OvertimeQueryController {

    private final OvertimeQueryService overtimeQueryService;

    @Autowired
    public OvertimeQueryController(OvertimeQueryService overtimeQueryService) {
        this.overtimeQueryService = overtimeQueryService;
    }

    /** 특정 직원의 특정 날짜에 승인된 연장근무신청 중 가장 늦은 종료시각 */
    @GetMapping("/latest-approved-end")
    public LocalDateTime latestApprovedEnd(
            @RequestParam UUID memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return overtimeQueryService.findLatestApprovedEndAt(memberId, date);
    }
}
