package com._team._team.member.publisher;

import com._team._team.event.MemberDeletedEvent;
import com._team._team.event.MemberSavedEvent;
import com._team._team.event.OrganizationDeletedEvent;
import com._team._team.event.OrganizationSavedEvent;
import com._team._team.member.domain.SearchOutboxEvent;
import com._team._team.member.repository.SearchOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchOutboxEventPublisher {

    private final SearchOutboxEventRepository searchOutboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // 5초마다 미처리 이벤트 발행
    @Scheduled(fixedDelay = 5000)
    @Transactional
    @SchedulerLock(
            name = "relaySearchOutboxEvents",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT5S"
    )
    public void publishPendingEvents() {
        List<SearchOutboxEvent> pendingEvents =
                searchOutboxEventRepository.findByProcessed("NO");

        for (SearchOutboxEvent event : pendingEvents) {
            try {
                Object payload;
                String topic = event.getTopic();

                if ("member-saved".equals(topic)) {
                    payload = objectMapper.readValue(
                            event.getPayload(), MemberSavedEvent.class);
                } else if ("member-deleted".equals(topic)) {
                    payload = objectMapper.readValue(
                            event.getPayload(), MemberDeletedEvent.class);
                } else if ("organization-saved".equals(topic)) {
                    payload = objectMapper.readValue(
                            event.getPayload(), OrganizationSavedEvent.class);
                } else if ("organization-deleted".equals(topic)) {
                    payload = objectMapper.readValue(
                            event.getPayload(), OrganizationDeletedEvent.class);
                } else {
                    log.warn("Unknown topic: {}", topic);
                    continue;
                }

                kafkaTemplate.send(topic, payload);
                event.setProcessed();
                log.info("Outbox 이벤트 발행 성공 topic: {} aggregateId: {}",
                        topic, event.getAggregateId());

            } catch (Exception e) {
                log.error("Outbox 이벤트 발행 실패: {} Error: {}",
                        event.getId(), e.getMessage());
            }
        }
    }

    // 매일 새벽 3시 처리 완료 데이터 삭제
    @Scheduled(cron = "0 0 3 * * *")
//    @Scheduled(cron = "30 * * * * *") // 테스트용
    @Transactional
    @SchedulerLock(
            name = "deleteProcessedOutboxEvents",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT1M"
    )
    public void deleteProcessedEvents() {
        // 처리 완료 후 24시간 지난 데이터 삭제 운영용
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        //테스트용
//        LocalDateTime threshold = LocalDateTime.now().plusHours(1);
        int deleted = searchOutboxEventRepository
                .deleteByProcessedAndCreatedAtBefore("YES", threshold);
        log.info("처리 완료 Outbox 이벤트 삭제 완료: {}건", deleted);
    }
}