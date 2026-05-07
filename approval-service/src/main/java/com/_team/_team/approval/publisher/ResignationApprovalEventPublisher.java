package com._team._team.approval.publisher;

import com._team._team.event.ResignationApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResignationApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public ResignationApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ResignationApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    ResignationApprovalEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[Resignation] event published. requestId={}, action={}, memberId={}, resignDate={}",
                    event.getRequestId(), event.getAction(),
                    event.getMemberId(), event.getResignDate());
        } catch (Exception e) {
            log.error("[Resignation] event publish failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}