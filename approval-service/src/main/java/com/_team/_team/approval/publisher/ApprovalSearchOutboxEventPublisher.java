package com._team._team.approval.publisher;

import com._team._team.approval.domain.ApprovalSearchOutboxEvent;
import com._team._team.approval.repository.ApprovalSearchOutboxRepository;
import com._team._team.event.ApprovalDeletedEvent;
import com._team._team.event.ApprovalSavedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalSearchOutboxEventPublisher {

    private final ApprovalSearchOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // 5초마다 미처리 이벤트 Kafka 발행
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<ApprovalSearchOutboxEvent> events =
                outboxRepository.findByProcessed("NO");

        for (ApprovalSearchOutboxEvent event : events) {
            try {
                Object payload = convertPayload(event.getTopic(), event.getPayload());
                kafkaTemplate.send(event.getTopic(),
                        event.getAggregateId().toString(),
                        payload);

                event.setProcessed();
                outboxRepository.save(event);

                log.info("[APPROVAL-OUTBOX] 발행 성공 topic={} aggregateId={}",
                        event.getTopic(), event.getAggregateId());
            } catch (Exception e) {
                log.error("[APPROVAL-OUTBOX] 발행 실패 topic={} aggregateId={} err={}",
                        event.getTopic(), event.getAggregateId(), e.getMessage());
            }
        }
    }

    // 새벽 3시 처리 완료된 이벤트 정리 (7일 이전)
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupProcessedEvents() {
        LocalDateTime before = LocalDateTime.now().minusDays(7);
        outboxRepository.deleteByProcessedAndCreatedAtBefore("YES", before);
        log.info("[APPROVAL-OUTBOX] 처리완료 이벤트 정리 완료 before={}", before);
    }

    private Object convertPayload(String topic, String payloadJson) throws Exception {
        return switch (topic) {
            case "approval-saved" ->
                    objectMapper.readValue(payloadJson, ApprovalSavedEvent.class);
            case "approval-deleted" ->
                    objectMapper.readValue(payloadJson, ApprovalDeletedEvent.class);
            default ->
                    throw new IllegalArgumentException("Unknown topic: " + topic);
        };
    }
}