package com._team._team.batch.attendance.worker;

import com._team._team.attendance.service.OvertimeRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * 사후 초과근무신청 72시간 경과 자동 만료 워커
 */
@Slf4j
@Component
public class OvertimeExpirationWorker {

    private final OvertimeRequestService overtimeRequestService;

    @Autowired
    public OvertimeExpirationWorker(OvertimeRequestService overtimeRequestService) {
        this.overtimeRequestService = overtimeRequestService;
    }

    // 사후 초과근무 신청 중 72시간 경과 건을 만료 처리
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        int expired = overtimeRequestService.expirePostOvertimeRequests(now);
        log.info("OvertimeExpirationWorker now={} expired={}", now, expired);
    }
}
