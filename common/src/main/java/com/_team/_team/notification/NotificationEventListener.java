package com._team._team.notification;

import com._team._team.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final KafkaMessagePublisher kafkaMessagePublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleNotificationMessage(NotificationMessage message) {
        try {
            // Kafka 발행만
            // Redis Pub/Sub은 member-service Kafka Consumer에서 처리
            kafkaMessagePublisher.publish("notification", message);
            log.info("알림 Kafka 발행 성공 receiverId: {}", message.getReceiverId());
        } catch (Exception e) {
            log.error("알림 Kafka 발행 실패: {}", message, e);
        }
    }
}