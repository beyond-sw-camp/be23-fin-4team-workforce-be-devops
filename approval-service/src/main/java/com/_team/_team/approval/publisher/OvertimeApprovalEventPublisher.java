package com._team._team.approval.publisher;

import com._team._team.event.OvertimeApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OvertimeApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public OvertimeApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(OvertimeApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    OvertimeApprovalEvent.TOPIC,
                    event.getRequestId().toString(), // key = requestId (파티셔닝 + 순서 보장)
                    event
            );
            log.info("[Overtime] event published. requestId={}, action={}, memberId={}",
                    event.getRequestId(), event.getAction(), event.getMemberId());
        } catch (Exception e) {
            log.error("[Overtime] event publish failed. requestId={}", event.getRequestId(), e);
            throw e; // 트랜잭션 롤백 유도
        }
    }
}