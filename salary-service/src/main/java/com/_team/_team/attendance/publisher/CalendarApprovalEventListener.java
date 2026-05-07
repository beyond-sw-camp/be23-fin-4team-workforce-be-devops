package com._team._team.attendance.publisher;

import com._team._team.event.CalendarApprovalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarApprovalEventListener {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handle(CalendarApprovalEvent event) {
        try {
            kafkaTemplate.send(
                    CalendarApprovalEvent.TOPIC,
                    event.getRequestId().toString(),
                    event
            );
            log.info("[Calendar] Kafka 발행 성공. requestId={}", event.getRequestId());
        } catch (Exception e) {
            log.error("[Calendar] Kafka 발행 실패. requestId={}", event.getRequestId(), e);
        }
    }
}
