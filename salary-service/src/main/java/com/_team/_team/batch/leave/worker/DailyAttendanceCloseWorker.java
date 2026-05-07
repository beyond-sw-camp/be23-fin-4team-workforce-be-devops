package com._team._team.batch.leave.worker;

import com._team._team.attendance.service.DailyAttendanceCloseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 일일 근태 2단계 마감 워커
 *  - 당일 데이터는 아직 진행중이라 "직전 영업일" 까지의 근태를 처리
 *  - 영업일 = 토/일 제외
 *
 * 시나리오:
 *   금요일 출근 후 퇴근 미체크
 *      → 토 02:00 배치 NOOP (토는 비영업일, 직전 영업일 = 금)
 *      → 일 02:00 배치 NOOP
 *      → 월 02:00 배치 → 직전 영업일 = 금요일 처리 (DRAFT 전이 + 알림)
 *      → 월 출근한 직원이 알림 보고 정정 신청 가능
 *      → 월 14:00 FINAL 배치 → 정정된 금요일분 처리 (UNDER_REVIEW 면 격리)
 */
@Slf4j
@Component
public class DailyAttendanceCloseWorker {

    private final DailyAttendanceCloseService dailyAttendanceCloseService;

    @Autowired
    public DailyAttendanceCloseWorker(DailyAttendanceCloseService dailyAttendanceCloseService) {
        this.dailyAttendanceCloseService = dailyAttendanceCloseService;
    }

    // DRAFT 실행, OPEN -> DRAFT 전이 + 재계산 + 이상감지
    public void runDraft() {
        if (isWeekend(LocalDate.now())) {
            log.info("DailyAttendanceCloseWorker[DRAFT] today={} 비영업일이라 NOOP",
                    LocalDate.now().getDayOfWeek());
            return;
        }

        LocalDate targetDate = lastBusinessDay(LocalDate.now().minusDays(1));
        DailyAttendanceCloseService.DraftResult result =
                dailyAttendanceCloseService.processDraft(targetDate);
        log.info("DailyAttendanceCloseWorker[DRAFT] date={} drafted={} failed={} leaveButClockIn={}",
                targetDate, result.drafted(), result.failed(), result.leaveButClockIn());
    }

    // FINAL 실행, DRAFT -> FINALIZED 전이 + 미퇴근 자동 마감
    public void runFinal() {
        if (isWeekend(LocalDate.now())) {
            log.info("DailyAttendanceCloseWorker[FINAL] today={} 비영업일이라 NOOP",
                    LocalDate.now().getDayOfWeek());
            return;
        }

        LocalDate targetDate = lastBusinessDay(LocalDate.now().minusDays(1));
        DailyAttendanceCloseService.FinalResult result =
                dailyAttendanceCloseService.processFinal(targetDate);
        log.info("DailyAttendanceCloseWorker[FINAL] date={} finalized={} autoClockOut={} failed={}",
                targetDate, result.finalized(), result.autoClockOut(), result.failed());
    }

    /** 토/일 건너뛰기 */
    private boolean isWeekend(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    /** from 부터 거슬러 올라가며 첫 영업일 (토/일 skip) 반환 */
    private LocalDate lastBusinessDay(LocalDate from) {
        LocalDate d = from;
        int safety = 14;
        while (isWeekend(d) && safety-- > 0) {
            d = d.minusDays(1);
        }
        return d;
    }
}
