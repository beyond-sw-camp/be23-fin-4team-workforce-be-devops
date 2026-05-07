package com._team._team.contract.service;

import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@Transactional
public class ContractNotificationService {

    private static final String TARGET_TYPE = "CONTRACT";
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ContractNotificationService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // 계약서 발송 시 직원에게 알림
    public void notifyContractSent(UUID employeeMemberId, UUID senderMemberId,
                                   UUID contractId, String templateName) {
        publish(employeeMemberId, senderMemberId,
                NotificationType.CONTRACT_SENT,
                "[" + templateName + "] 전자계약서가 도착했습니다. 확인 후 서명해주세요.",
                contractId);
    }

//    직원 서명 완료 시 발송자(인사팀)에게 알림
    public void notifyContractSigned(UUID senderMemberId, UUID employeeMemberId,
                                     UUID contractId, String employeeName) {
        publish(senderMemberId, employeeMemberId,
                NotificationType.CONTRACT_SIGNED,
                employeeName + "님이 전자계약서에 서명했습니다.",
                contractId);
    }


    private void publish(UUID receiverId, UUID senderId,
                         NotificationType type, String content, UUID targetId) {
        if (receiverId == null) {
            log.warn("계약 알림 수신자 없음 — skip. type={}, targetId={}", type, targetId);
            return;
        }
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(receiverId)
                .senderId(senderId)
                .notificationType(type)
                .content(content)
                .targetId(targetId)
                .targetType(TARGET_TYPE)
                .build());
    }

    public void notifyContractRejected(UUID senderMemberId, UUID employeeMemberId,
                                       UUID contractId, String employeeName, String reason) {
        publish(senderMemberId, employeeMemberId,
                NotificationType.CONTRACT_REJECTED,
                employeeName + "님이 전자계약서를 거절했습니다. 사유: " + reason,
                contractId);
    }

    public void notifyContractCanceled(UUID employeeMemberId, UUID senderMemberId,
                                       UUID contractId, String templateName) {
        publish(employeeMemberId, senderMemberId,
                NotificationType.CONTRACT_CANCELED,
                "[" + templateName + "] 전자계약서가 회수되었습니다.",
                contractId);
    }

    // 미서명 리마인드 알림
    public void notifySignReminder(UUID employeeMemberId, UUID senderMemberId,
                                   UUID contractId, String templateName) {
        publish(employeeMemberId, senderMemberId,
                NotificationType.CONTRACT_REMIND,
                "[" + templateName + "] 서명 대기 중인 전자계약서가 있습니다. 확인해주세요.",
                contractId);
    }

}
