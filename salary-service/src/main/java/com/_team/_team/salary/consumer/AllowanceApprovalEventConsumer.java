package com._team._team.salary.consumer;

import com._team._team.event.AllowanceApprovalEvent;
import com._team._team.salary.service.MemberAllowanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

/**
 * 수당 결재 이벤트 Consumer
 */
@Slf4j
@Component
public class AllowanceApprovalEventConsumer {

    private final MemberAllowanceService memberAllowanceService;

    @Autowired
    public AllowanceApprovalEventConsumer(MemberAllowanceService memberAllowanceService) {
        this.memberAllowanceService = memberAllowanceService;
    }

    @KafkaListener(
            topics = AllowanceApprovalEvent.TOPIC,
            groupId = "salary-service",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.AllowanceApprovalEvent"
            }
    )
    public void consume(AllowanceApprovalEvent event) {
        log.info("[Allowance] event received. requestId={}, action={}, memberId={}",
                event.getRequestId(), event.getAction(), event.getMemberId());

        try {
            switch (event.getAction()) {
                case APPROVE -> memberAllowanceService.applyApproval(
                        event.getRequestId(),
                        event.getApproverId(),
                        event.getDecidedAt());

                case REJECT, CANCEL -> memberAllowanceService.applyRejection(
                        event.getRequestId(),
                        event.getApproverId(),
                        event.getDecidedAt(),
                        event.getNote());
            }
        } catch (Exception e) {
            log.error("[Allowance] consume failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}