package com._team._team.personnel.consumer;

import com._team._team.event.PersonnelOrderApprovedEvent;
import com._team._team.personnel.service.PersonnelOrderApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 인사발령 결재 승인 Kafka 수신
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonnelOrderApprovalConsumer {

    private final PersonnelOrderApplyService applyService;

    @KafkaListener(
            topics = PersonnelOrderApprovedEvent.TOPIC,
            groupId = "member-service-personnel-order",
            containerFactory = "personnelOrderListenerContainerFactory",
            properties = { "auto.offset.reset=latest" }
    )
    public void consume(PersonnelOrderApprovedEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            log.warn("[PersonnelOrder] no items - skip.");
            return;
        }
        log.info("[PersonnelOrder] received. approvalDocumentId={} items={}",
                event.getApprovalDocumentId(), event.getItems().size());
        applyService.apply(event);
    }
}
