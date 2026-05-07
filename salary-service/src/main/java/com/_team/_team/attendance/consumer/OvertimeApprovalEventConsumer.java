package com._team._team.attendance.consumer;

import com._team._team.attendance.service.OvertimeApprovalService;
import com._team._team.event.OvertimeApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OvertimeApprovalEventConsumer {

    private final OvertimeApprovalService overtimeApprovalService;

    @Autowired
    public OvertimeApprovalEventConsumer(OvertimeApprovalService overtimeApprovalService) {
        this.overtimeApprovalService = overtimeApprovalService;
    }

    @KafkaListener(
            topics = OvertimeApprovalEvent.TOPIC,
            groupId = "salary-service",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.OvertimeApprovalEvent"
            }
    )
    public void consume(OvertimeApprovalEvent event) {
        log.info("[Overtime] event received. requestId={}, action={}",
                event.getRequestId(), event.getAction());

        try {
            switch (event.getAction()) {
                case APPROVE -> overtimeApprovalService.applyApproval(event);
                case REJECT, CANCEL -> overtimeApprovalService.applyRejection(event);
            }
        } catch (Exception e) {
            log.error("[Overtime] consume failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}


