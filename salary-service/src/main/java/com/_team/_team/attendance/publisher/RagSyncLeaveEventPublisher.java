package com._team._team.attendance.publisher;

import com._team._team.event.RagSyncLeaveEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 휴가 정책 변경 이벤트 발행 (RAG 동기화용).
 * CompanyLeaveType, LeavePolicy 변경 시 ai-service로 Kafka 이벤트 전송.
 */
@Slf4j
@Component
public class RagSyncLeaveEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public RagSyncLeaveEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(RagSyncLeaveEvent event) {
        try {
            kafkaTemplate.send(
                    RagSyncLeaveEvent.TOPIC,
                    event.getCompanyId().toString(),
                    event
            );
            log.info("[RagSyncLeave] published. companyId={}, action={}, resource={}",
                    event.getCompanyId(), event.getAction(), event.getResourceType());
        } catch (Exception e) {
            log.error("[RagSyncLeave] publish failed. companyId={}",
                    event.getCompanyId(), e);
            throw e;
        }
    }
}