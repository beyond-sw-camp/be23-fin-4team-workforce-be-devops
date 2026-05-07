package com._team._team.attendance.consumer;

import com._team._team.attendance.service.MemberScheduleSelectionService;
import com._team._team.event.ScheduleApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

/**
 * 슬롯 선택 결재 완료 이벤트 Consumer
 * approval-service 가 쏜 schedule-approval 토픽을 구독
 */
@Slf4j
@Component
public class ScheduleApprovalEventConsumer {

    private final MemberScheduleSelectionService memberScheduleSelectionService;

    @Autowired
    public ScheduleApprovalEventConsumer(
            MemberScheduleSelectionService memberScheduleSelectionService) {
        this.memberScheduleSelectionService = memberScheduleSelectionService;
    }

    @KafkaListener(
            topics = ScheduleApprovalEvent.TOPIC,
            groupId = "salary-service",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.ScheduleApprovalEvent"
            }
    )
    public void consume(ScheduleApprovalEvent event) {
        log.info("[Schedule] event received. requestId={}, selectionId={}, action={}",
                event.getRequestId(), event.getSelectionId(), event.getAction());

        try {
            switch (event.getAction()) {
                case APPROVE -> memberScheduleSelectionService.applyApproval(
                        event.getSelectionId(),
                        event.getRequestId(),
                        event.getApproverId(),
                        event.getDecidedAt()
                );
                case REJECT, CANCEL -> memberScheduleSelectionService.applyRejection(
                        event.getSelectionId(),
                        event.getRequestId(),
                        event.getApproverId(),
                        event.getDecidedAt(),
                        event.getNote()
                );
            }
        } catch (Exception e) {
            log.error("[Schedule] consume failed. requestId={}", event.getRequestId(), e);
            throw e;
        }
    }
}