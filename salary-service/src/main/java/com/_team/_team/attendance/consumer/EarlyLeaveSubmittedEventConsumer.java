package com._team._team.attendance.consumer;

import com._team._team.attendance.service.EarlyLeaveService;
import com._team._team.event.EarlyLeaveSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

/**
 * 조퇴계 결재 상신 이벤트 Consumer
 * 일일근태 UNDER_REVIEW 격리 + 조퇴 시각 입력 + 정정 로그 추가
 */
@Slf4j
@Component
public class EarlyLeaveSubmittedEventConsumer {

    private final EarlyLeaveService earlyLeaveService;

    @Autowired
    public EarlyLeaveSubmittedEventConsumer(EarlyLeaveService earlyLeaveService) {
        this.earlyLeaveService = earlyLeaveService;
    }

    @KafkaListener(
            topics = EarlyLeaveSubmittedEvent.TOPIC,
            groupId = "salary-service",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.EarlyLeaveSubmittedEvent"
            }
    )
    public void consume(EarlyLeaveSubmittedEvent event) {
        log.info("[EarlyLeaveSubmit] event received. requestId={}, memberId={}, date={}",
                event.getRequestId(), event.getMemberId(), event.getAttendanceDate());

        try {
            earlyLeaveService.submitEarlyLeave(
                    event.getCompanyId(),
                    event.getMemberId(),
                    event.getRequestId(),
                    event.getAttendanceDate(),
                    event.getEarlyLeaveAt(),
                    event.getReason());
        } catch (Exception e) {
            log.error("[EarlyLeaveSubmit] consume failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
