package com._team._team.attendance.feignClients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(name = "approval-service", url = "${feign.url.approval-service:}")
public interface ApprovalServiceClient {

    /**
     * 특정 직원의 특정 날짜에 승인된 연장근무신청 중 가장 늦은 종료시각 -> 없으면 null
     */
    @GetMapping("/approval/overtime/latest-approved-end")
    LocalDateTime findLatestApprovedEndAt(
            @RequestParam("memberId") UUID memberId,
            @RequestParam("date") LocalDate date
    );
}
