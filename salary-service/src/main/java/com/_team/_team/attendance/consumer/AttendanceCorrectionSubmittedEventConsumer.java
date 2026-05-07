package com._team._team.attendance.consumer;

import com._team._team.attendance.service.AttendanceCorrectionService;
import com._team._team.event.AttendanceCorrectionSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

/**
 * 근태정정 결재 상신 이벤트 Consumer
 * 일일근태 UNDER_REVIEW (검토중) + 정정 시각 입력 + 정정 로그 추가
 */
@Slf4j
@Component
public class AttendanceCorrectionSubmittedEventConsumer {

    private final AttendanceCorrectionService attendanceCorrectionService;

    @Autowired
    public AttendanceCorrectionSubmittedEventConsumer(AttendanceCorrectionService attendanceCorrectionService) {
        this.attendanceCorrectionService = attendanceCorrectionService;
    }

    @KafkaListener(
            topics = AttendanceCorrectionSubmittedEvent.TOPIC,
            groupId = "salary-service",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.AttendanceCorrectionSubmittedEvent"
            }
    )
    public void consume(AttendanceCorrectionSubmittedEvent event) {
        log.info("[AttendanceCorrectionSubmit] event received. requestId={}, memberId={}, date={}",
                event.getRequestId(), event.getMemberId(), event.getAttendanceDate());

        try {
            attendanceCorrectionService.submitCorrection(
                    event.getCompanyId(),
                    event.getMemberId(),
                    event.getRequestId(),
                    event.getAttendanceDate(),
                    event.getRequestedClockIn(),
                    event.getRequestedClockOut(),
                    event.getReason());
        } catch (Exception e) {
            log.error("[AttendanceCorrectionSubmit] consume failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
