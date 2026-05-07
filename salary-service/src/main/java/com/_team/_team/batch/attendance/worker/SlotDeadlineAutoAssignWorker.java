package com._team._team.batch.attendance.worker;

import com._team._team.attendance.service.SlotDeadlineAutoAssignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

/**
 * 스케줄 슬롯 선택 마감일 경과 기본 슬롯 자동 할당 워커
 * 어제가 마감일이었던 회사 대상으로 처리
 */
@Slf4j
@Component
public class SlotDeadlineAutoAssignWorker {

    private final SlotDeadlineAutoAssignService slotDeadlineAutoAssignService;

    @Autowired
    public SlotDeadlineAutoAssignWorker(SlotDeadlineAutoAssignService service) {
        this.slotDeadlineAutoAssignService = service;
    }

    public void run() {
        LocalDate today = LocalDate.now();
        SlotDeadlineAutoAssignService.AssignResult result =
                slotDeadlineAutoAssignService.runForDate(today);
        log.info("SlotDeadlineAutoAssignWorker today={} schedules={} assigned={}",
                today, result.targetSchedules(), result.assignedMembers());
    }
}
