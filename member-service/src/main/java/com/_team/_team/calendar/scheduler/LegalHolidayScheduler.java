package com._team._team.calendar.scheduler;

import com._team._team.calendar.service.LegalHolidayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
public class LegalHolidayScheduler {

    private final LegalHolidayService legalHolidayService;

    @Autowired
    public LegalHolidayScheduler(LegalHolidayService legalHolidayService) {
        this.legalHolidayService = legalHolidayService;
    }

    // 매년 1월 1일 새벽 1시 다음 해 공휴일 수집
    @Scheduled(cron = "0 0 1 1 1 *")
    @SchedulerLock(
            name = "collectLegalHolidays",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT1M"
    )
    public void collectNextYearHolidays() {
        int nextYear = LocalDate.now().getYear() + 1;
        log.info("내년 공휴일 수집 시작 year: {}", nextYear);
        legalHolidayService.collectAndSaveHolidays(nextYear);
    }

    // 서버 시작 시 당해 공휴일 없으면 자동 수집
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void collectCurrentYearHolidaysOnStartup() {
        int currentYear = LocalDate.now().getYear();
        if (!legalHolidayService.hasHolidays(currentYear)) {
            log.info("당해 공휴일 없음 자동 수집 year: {}", currentYear);
            legalHolidayService.collectAndSaveHolidays(currentYear);
        }
    }
}
