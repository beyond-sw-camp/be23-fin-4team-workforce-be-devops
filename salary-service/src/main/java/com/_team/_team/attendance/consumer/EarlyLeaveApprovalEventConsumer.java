package com._team._team.attendance.consumer;

import com._team._team.attendance.service.EarlyLeaveService;
import com._team._team.event.EarlyLeaveApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

/**
 * 조퇴계 결재 처리 이벤트 Consumer
 */
@Slf4j
@Component
public class EarlyLeaveApprovalEventConsumer {

    private final EarlyLeaveService earlyLeaveService;

    @Autowired
    public EarlyLeaveApprovalEventConsumer(EarlyLeaveService earlyLeaveService) {
        this.earlyLeaveService = earlyLeaveService;
    }

    @KafkaListener(
            topics = EarlyLeaveApprovalEvent.TOPIC,
            groupId = "salary-service",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.EarlyLeaveApprovalEvent"
            }
    )
    public void consume(EarlyLeaveApprovalEvent event) {
        log.info("[EarlyLeave] event received. requestId={}, action={}, memberId={}, date={}",
                event.getRequestId(), event.getAction(),
                event.getMemberId(), event.getAttendanceDate());

        try {
            switch (event.getAction()) {
                case APPROVE -> earlyLeaveService.applyApprovedEarlyLeave(
                        event.getCompanyId(),
                        event.getMemberId(),
                        event.getApproverId(),
                        event.getAttendanceDate(),
                        event.getEarlyLeaveAt(),
                        event.getReason());
                case REJECT, CANCEL -> earlyLeaveService.cancelSubmittedEarlyLeave(
                        event.getCompanyId(),
                        event.getMemberId(),
                        event.getAttendanceDate());
            }
        } catch (Exception e) {
            log.error("[EarlyLeave] consume failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}
