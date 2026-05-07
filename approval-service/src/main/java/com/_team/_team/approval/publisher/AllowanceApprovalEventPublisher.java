package com._team._team.approval.publisher;

import com._team._team.event.AllowanceApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 수당 변경 결재 이벤트 발행
 */
@Slf4j
@Component
public class AllowanceApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public AllowanceApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // Kafka 로 이벤트 발송
    public void publish(AllowanceApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    AllowanceApprovalEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[Allowance] event published. requestId={}, action={}, memberId={}",
                    event.getRequestId(), event.getAction(), event.getMemberId());
        } catch (Exception e) {
            log.error("[Allowance] event publish failed. requestId={}",
                    event.getRequestId(), e);
            throw e;
        }
    }
}