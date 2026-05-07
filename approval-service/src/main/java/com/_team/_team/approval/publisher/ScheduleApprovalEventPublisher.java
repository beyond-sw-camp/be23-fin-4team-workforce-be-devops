package com._team._team.approval.publisher;
import com._team._team.event.ScheduleApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 시차출퇴근 스케줄 선택 결재 이벤트 발행
 * approval-service 에서 승인·반려·취소 시점에 salary-service 로 쏘는 Kafka publisher
 */
@Slf4j
@Component
public class ScheduleApprovalEventPublisher {


    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public ScheduleApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ScheduleApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    ScheduleApprovalEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[Schedule] event published. requestId={}, action={}, memberId={}",
                    event.getRequestId(), event.getAction(), event.getMemberId());
        } catch (Exception e) {
            log.error("[Schedule] event publish failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
