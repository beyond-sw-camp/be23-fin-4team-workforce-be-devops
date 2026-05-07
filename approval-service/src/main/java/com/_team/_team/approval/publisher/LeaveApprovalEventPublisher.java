package com._team._team.approval.publisher;

import com._team._team.event.LeaveApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 휴가 결재 이벤트 발행
 * approval-service에서 승인/반려 시점에 salary-service로 쏘는 Kafka publisher
 * 토픽은 leave-approval, key는 requestId 문자열 (같은 건은 같은 파티션으로 순서 보장)
 */
@Slf4j
@Component
public class LeaveApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public LeaveApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // Kafka로 이벤트 발송
    // 실패 시 예외 던져서 호출한 쪽 트랜잭션이 롤백되도록 유도
    public void publish(LeaveApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    LeaveApprovalEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[Leave] event published. requestId={}, action={}, memberId={}, days={}",
                    event.getRequestId(), event.getAction(),
                    event.getMemberId(), event.getDays());
        } catch (Exception e) {
            log.error("[Leave] event publish failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
