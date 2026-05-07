package com._team._team.attendance.publisher;

import com._team._team.event.RagSyncAttendanceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RagSyncAttendanceEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public RagSyncAttendanceEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(RagSyncAttendanceEvent event) {
        try {
            kafkaTemplate.send(
                    RagSyncAttendanceEvent.TOPIC,
                    event.getCompanyId().toString(),
                    event
            );
            log.info("[RagSyncAttendance] published. companyId={}, action={}, resource={}",
                    event.getCompanyId(), event.getAction(), event.getResourceType());
        } catch (Exception e) {
            log.error("[RagSyncAttendance] publish failed. companyId={}",
                    event.getCompanyId(), e);
            throw e;
        }
    }
}