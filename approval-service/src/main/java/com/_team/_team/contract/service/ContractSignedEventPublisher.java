package com._team._team.contract.service;

import com._team._team.event.ContractSignedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ContractSignedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public ContractSignedEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ContractSignedEvent event) {
        try {
            kafkaTemplate.send(
                    ContractSignedEvent.TOPIC,
                    event.getContractId().toString(),
                    event
            );
            log.info("[Contract] signed event published. contractId={}, memberId={}, newSalary={}",
                    event.getContractId(), event.getMemberId(), event.getNewSalary());
        } catch (Exception e) {
            log.error("[Contract] signed event publish failed. contractId={}",
                    event.getContractId(), e);
            throw e;
        }
    }
}
