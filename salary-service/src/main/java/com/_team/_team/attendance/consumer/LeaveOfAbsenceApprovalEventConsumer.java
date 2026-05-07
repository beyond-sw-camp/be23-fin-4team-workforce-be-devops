package com._team._team.attendance.consumer;

import com._team._team.attendance.service.MemberLeaveOfAbsenceService;
import com._team._team.event.LeaveOfAbsenceApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

/**
 * 휴직 결재 이벤트 Consumer
 * APPROVE: LeaveOfAbsence 상태 ACTIVE
 * REJECT: 상태 REJECTED
 */
@Slf4j
@Component
public class LeaveOfAbsenceApprovalEventConsumer {

    private final MemberLeaveOfAbsenceService memberLeaveOfAbsenceService;

    @Autowired
    public LeaveOfAbsenceApprovalEventConsumer(MemberLeaveOfAbsenceService memberLeaveOfAbsenceService) {
        this.memberLeaveOfAbsenceService = memberLeaveOfAbsenceService;
    }

    @KafkaListener(
            topics = LeaveOfAbsenceApprovalEvent.TOPIC,
            groupId = "salary-service",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.LeaveOfAbsenceApprovalEvent"
            }
    )
    public void consume(LeaveOfAbsenceApprovalEvent event) {
        log.info("[LeaveOfAbsence] event received. requestId={}, action={}, memberId={}",
                event.getRequestId(), event.getAction(), event.getMemberId());

        try {
            switch (event.getAction()) {
                case APPROVE -> memberLeaveOfAbsenceService.applyApproval(
                        event.getRequestId(),
                        event.getApproverId(),
                        event.getDecidedAt());

                case REJECT -> memberLeaveOfAbsenceService.applyRejection(
                        event.getRequestId(),
                        event.getApproverId(),
                        event.getDecidedAt(),
                        event.getNote());
            }
        } catch (Exception e) {
            log.error("[LeaveOfAbsence] consume failed. requestId={}",
                    event.getRequestId(), e);
            throw e;
        }
    }
}