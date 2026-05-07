package com._team._team.approval.publisher;

import com._team._team.event.AttendanceCorrectionApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AttendanceCorrectionApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public AttendanceCorrectionApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(AttendanceCorrectionApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    AttendanceCorrectionApprovalEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[AttendanceCorrection] event published. requestId={}, action={}, memberId={}, date={}",
                    event.getRequestId(), event.getAction(),
                    event.getMemberId(), event.getAttendanceDate());
        } catch (Exception e) {
            log.error("[AttendanceCorrection] event publish failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
