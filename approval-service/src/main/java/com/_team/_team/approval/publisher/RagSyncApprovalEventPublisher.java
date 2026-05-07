package com._team._team.approval.publisher;

import com._team._team.event.RagSyncApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RagSyncApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public RagSyncApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(RagSyncApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    RagSyncApprovalEvent.TOPIC,
                    event.getCompanyId().toString(),
                    event
            );
            log.info("[RagSyncApproval] published. companyId={}, action={}, resource={}",
                    event.getCompanyId(), event.getAction(), event.getResourceType());
        } catch (Exception e) {
            log.error("[RagSyncApproval] publish failed. companyId={}",
                    event.getCompanyId(), e);
            throw e;
        }
    }
}