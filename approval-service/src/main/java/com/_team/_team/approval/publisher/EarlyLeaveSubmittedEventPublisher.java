package com._team._team.approval.publisher;

import com._team._team.event.EarlyLeaveSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EarlyLeaveSubmittedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public EarlyLeaveSubmittedEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(EarlyLeaveSubmittedEvent event) {
        try {
            kafkaTemplate.send(
                    EarlyLeaveSubmittedEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[EarlyLeaveSubmit] event published. requestId={}, memberId={}, date={}",
                    event.getRequestId(), event.getMemberId(), event.getAttendanceDate());
        } catch (Exception e) {
            log.error("[EarlyLeaveSubmit] publish failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
