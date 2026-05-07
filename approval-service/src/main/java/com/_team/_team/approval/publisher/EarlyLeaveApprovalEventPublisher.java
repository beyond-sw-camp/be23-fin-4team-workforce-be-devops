package com._team._team.approval.publisher;

import com._team._team.event.EarlyLeaveApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EarlyLeaveApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public EarlyLeaveApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(EarlyLeaveApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    EarlyLeaveApprovalEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[EarlyLeave] event published. requestId={}, action={}, memberId={}, date={}",
                    event.getRequestId(), event.getAction(),
                    event.getMemberId(), event.getAttendanceDate());
        } catch (Exception e) {
            log.error("[EarlyLeave] event publish failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
