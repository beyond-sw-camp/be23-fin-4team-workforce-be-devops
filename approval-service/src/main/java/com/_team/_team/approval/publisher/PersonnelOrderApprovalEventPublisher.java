package com._team._team.approval.publisher;

import com._team._team.event.PersonnelOrderApprovedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PersonnelOrderApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public PersonnelOrderApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PersonnelOrderApprovedEvent event) {
        try {
            kafkaTemplate.send(
                    PersonnelOrderApprovedEvent.TOPIC,
                    event.getApprovalDocumentId() != null
                            ? event.getApprovalDocumentId().toString()
                            : "personnel-order",
                    event
            );
            log.info("[PersonnelOrder] event published. approvalDocumentId={} items={}",
                    event.getApprovalDocumentId(),
                    event.getItems() != null ? event.getItems().size() : 0);
        } catch (Exception e) {
            log.error("[PersonnelOrder] event publish failed. approvalDocumentId={}",
                    event.getApprovalDocumentId(), e);
            throw e;
        }
    }
}
