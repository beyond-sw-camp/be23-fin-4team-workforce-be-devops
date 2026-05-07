package com._team._team.salary.publisher;

import com._team._team.event.RagSyncSalaryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RagSyncSalaryEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public RagSyncSalaryEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(RagSyncSalaryEvent event) {
        try {
            kafkaTemplate.send(
                    RagSyncSalaryEvent.TOPIC,
                    event.getCompanyId().toString(),
                    event
            );
            log.info("[RagSyncSalary] published. companyId={}, action={}, resource={}",
                    event.getCompanyId(), event.getAction(), event.getResourceType());
        } catch (Exception e) {
            log.error("[RagSyncSalary] publish failed. companyId={}",
                    event.getCompanyId(), e);
            throw e;
        }
    }
}