package com._team._team.approval.publisher;

import com._team._team.event.BusinessTripApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 출장 결재 이벤트 발행
 */
@Slf4j
@Component
public class BusinessTripApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public BusinessTripApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(BusinessTripApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    BusinessTripApprovalEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[BusinessTrip] event published. requestId={}, action={}, memberId={}, total={}",
                    event.getRequestId(), event.getAction(), event.getMemberId(), event.getTotalAmount());
        } catch (Exception e) {
            log.error("[BusinessTrip] event publish failed. requestId={}",
                    event.getRequestId(), e);
            throw e;
        }
    }
}
