package com._team._team.batch.attendance.worker;

import com._team._team.attendance.service.MonthlyAttendanceCloseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

// 월 장부 마감 워커
@Slf4j
@Component
public class MonthlyAttendanceCloseWorker {

    private final MonthlyAttendanceCloseService monthlyAttendanceCloseService;

    @Autowired
    public MonthlyAttendanceCloseWorker(MonthlyAttendanceCloseService service) {
        this.monthlyAttendanceCloseService = service;
    }

    // 글로벌, 오늘 기준 전월 대상
    public void run() {
        YearMonth targetMonth = YearMonth.from(LocalDate.now()).minusMonths(1);
        MonthlyAttendanceCloseService.CloseResult result =
                monthlyAttendanceCloseService.processForMonth(targetMonth);
        log.info("MonthlyAttendanceCloseWorker month={} companies={} created={} skipped={} failed={}",
                targetMonth, result.companiesProcessed(), result.ledgersCreated(),
                result.ledgersSkipped(), result.failed());
    }

    // 회사별, 회사 1곳 전월 마감
    public void runForCompany(UUID companyId) {
        YearMonth targetMonth = YearMonth.from(LocalDate.now()).minusMonths(1);
        MonthlyAttendanceCloseService.CloseResult result =
                monthlyAttendanceCloseService.processForCompany(companyId, targetMonth);
        log.info("MonthlyAttendanceCloseWorker companyId={} month={} created={} skipped={} failed={}",
                companyId, targetMonth, result.ledgersCreated(),
                result.ledgersSkipped(), result.failed());
    }
}