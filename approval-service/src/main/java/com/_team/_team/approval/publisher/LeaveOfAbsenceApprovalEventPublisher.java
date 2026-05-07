package com._team._team.approval.publisher;

import com._team._team.event.CalendarApprovalEvent;
import com._team._team.event.LeaveOfAbsenceApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LeaveOfAbsenceApprovalEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public LeaveOfAbsenceApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(LeaveOfAbsenceApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    LeaveOfAbsenceApprovalEvent.TOPIC,
                    event.getRequestId().toString(),   // key, 파티션 순서 보장
                    event
            );
            log.info("[LeaveOfAbsence] event published. requestId={}, action={}, memberId={}",
                    event.getRequestId(), event.getAction(), event.getMemberId());
        } catch (Exception e) {
            log.error("[LeaveOfAbsence] event publish failed. requestId={}",
                    event.getRequestId(), e);
            throw e;   // 트랜잭션 롤백 유도
        }
    }

    @Slf4j
    @Component
    public static class CalendarApprovalEventPublisher {

        private final KafkaTemplate<String, Object> kafkaTemplate;

        public CalendarApprovalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
        }

        public void publish(CalendarApprovalEvent event) {
            try {
                kafkaTemplate.send(
                        CalendarApprovalEvent.TOPIC,
                        event.getRequestId().toString(),
                        event
                );
                log.info("[Calendar] event published. requestId={}, title={}",
                        event.getRequestId(), event.getTitle());
            } catch (Exception e) {
                log.error("[Calendar] event publish failed. requestId={}",
                        event.getRequestId(), e);
            }
        }
    }
}