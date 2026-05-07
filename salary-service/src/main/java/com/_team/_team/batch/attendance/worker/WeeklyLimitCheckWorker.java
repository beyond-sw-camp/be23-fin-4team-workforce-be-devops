package com._team._team.batch.attendance.worker;

import com._team._team.attendance.service.WeeklyLimitCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 주 52시간 초과 감지 워커
 */
@Slf4j
@Component
public class WeeklyLimitCheckWorker {

    private final WeeklyLimitCheckService weeklyLimitCheckService;

    @Autowired
    public WeeklyLimitCheckWorker(WeeklyLimitCheckService weeklyLimitCheckService) {
        this.weeklyLimitCheckService = weeklyLimitCheckService;
    }

    // 03:00 실행, 어제 기준 주간 한도 검증
    public void run() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        WeeklyLimitCheckService.CheckResult result =
                weeklyLimitCheckService.checkForDate(targetDate);
        log.info("WeeklyLimitCheckWorker date={} checked={} violated={}",
                targetDate, result.checked(), result.violated());
    }
}
