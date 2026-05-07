package com._team._team.approval.publisher;

import com._team._team.event.AttendanceCorrectionSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AttendanceCorrectionSubmittedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public AttendanceCorrectionSubmittedEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(AttendanceCorrectionSubmittedEvent event) {
        try {
            kafkaTemplate.send(
                    AttendanceCorrectionSubmittedEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[AttendanceCorrectionSubmit] event published. requestId={}, memberId={}, date={}",
                    event.getRequestId(), event.getMemberId(), event.getAttendanceDate());
        } catch (Exception e) {
            log.error("[AttendanceCorrectionSubmit] publish failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
