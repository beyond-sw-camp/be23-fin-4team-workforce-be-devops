package com._team._team.approval.service;

import com._team._team.approval.domain.Approval;
import com._team._team.approval.domain.ApprovalRequest;
import com._team._team.approval.domain.ApprovalViewer;
import com._team._team.approval.domain.enums.ViewerType;
import com._team._team.approval.repository.AbsenceProxyRepository;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class ApprovalNotificationService {

    private static final String TARGET_TYPE = "APPROVAL";
    private static final String OFFICIAL_TOPIC = "official-approved";
    private final ApplicationEventPublisher eventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AbsenceProxyRepository absenceProxyRepository;


    @Autowired
    public ApprovalNotificationService(ApplicationEventPublisher eventPublisher,
                                       KafkaTemplate<String, Object> kafkaTemplate, AbsenceProxyRepository absenceProxyRepository) {
        this.eventPublisher = eventPublisher;
        this.kafkaTemplate = kafkaTemplate;
        this.absenceProxyRepository = absenceProxyRepository;
    }

    // 공문 최종 승인 도메인 이벤트 (별도 토픽)
    // 기존 알림은 eventPublisher 경로(AFTER_COMMIT) 로 나가고,
    // 이 메서드는 official-approved 토픽에만 추가로 발행한다.
    public void publishOfficialApprovedEvent(ApprovalRequest request) {
        if (request == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", request.getRequestId());
        payload.put("documentNumber", request.getDocumentNumber());
        payload.put("requesterId", request.getMemberId());
        payload.put("requesterOrgId", request.getRequesterOrganizationId());
        payload.put("requesterOrgName", request.getRequesterOrganizationName());
        payload.put("approvedAt", request.getUpdatedAt());

        kafkaTemplate.send(OFFICIAL_TOPIC, payload);
        log.info("공문 승인 이벤트 발행 requestId={}, docNumber={}",
                request.getRequestId(), request.getDocumentNumber());
    }

    //    결재 요청 (WAIT 진입)
    //    첫 결재자: APPROVAL_REQUESTED / 참조자: APPROVAL_REFERENCED
    public void notifyApprovalRequested(ApprovalRequest request, Approval firstApproval, List<ApprovalViewer> viewers){

        String title = title(request);
        UUID requesterId = request.getMemberId();
        UUID requestId = request.getRequestId();
        UUID firstApproverId = firstApproval.getApproverMemberId();

        //        첫 결재자 알림
        publish(firstApproverId, requesterId,
                NotificationType.APPROVAL_REQUESTED,
                title + " 결재 요청이 도착했습니다.",
                requestId);

        // ★ 첫 결재자에게 활성 위임이 있으면 수임자에게도 알림
        notifyProxyIfExists(firstApproverId, requesterId, title, requestId);

        //        참조자 알림 (요청자/첫 결재자 본인 제외)
        Set<UUID> notified = new HashSet<>();
        notified.add(requesterId);
        notified.add(firstApproverId);

        if (viewers != null) {
            viewers.stream()
                    .filter(v -> v.getViewerType() == ViewerType.CC)
                    .filter(v -> notified.add(v.getViewerMemberId()))
                    .forEach(v -> publish(
                            v.getViewerMemberId(), requesterId,
                            NotificationType.APPROVAL_REFERENCED,
                            title + " 결재 문서에 참조자로 지정되었습니다.",
                            requestId));
        }
    }

//    다음 결재자 활성화
//    다음 결재자: APPROVAL_REQUESTED
    public void notifyNextApprover(ApprovalRequest request, Approval nextApproval, UUID senderId){

        String title = title(request);
        publish(nextApproval.getApproverMemberId(), senderId, NotificationType.APPROVAL_REQUESTED,
                title + "대기 중인 결재가 있습니다.",
                request.getRequestId());

        // ★ 다음 결재자에게 활성 위임이 있으면 수임자에게도 알림
        notifyProxyIfExists(nextApproval.getApproverMemberId(), senderId, title, request.getRequestId());
    }

    //    최종 승인 완료
    //    요청자/참조자: APPROVAL_APPROVED / 공람자: APPROVAL_CIRCULATED
    //    (요청자 > 참조자 > 공람자 우선순위로 중복 제거)
    public void notifyApproved(ApprovalRequest request,
                               List<ApprovalViewer> viewers,
                               UUID senderId) {
        String title = title(request);
        UUID requesterId = request.getMemberId();
        UUID requestId = request.getRequestId();

        List<Receiver> receivers = resolveCompletionReceivers(requesterId, viewers);

        for (Receiver r : receivers) {
            if (r.role == Role.VIEWER) {
                publish(r.memberId, senderId,
                        NotificationType.APPROVAL_CIRCULATED,
                        title + " 결재 문서가 공람 대상으로 도착했습니다.",
                        requestId);
            } else {
                publish(r.memberId, senderId,
                        NotificationType.APPROVAL_APPROVED,
                        title + " 결재가 승인되었습니다.",
                        requestId);
            }
        }
    }

    //    반려
    //    요청자/참조자: APPROVAL_REJECTED (공람자/이전 결재자 알림 없음)
    public void notifyRejected(ApprovalRequest request,
                               List<ApprovalViewer> viewers,
                               UUID senderId,
                               String reason) {
        String title = title(request);
        UUID requesterId = request.getMemberId();
        UUID requestId = request.getRequestId();

        Set<UUID> notified = new HashSet<>();

        //        요청자
        notified.add(requesterId);
        publish(requesterId, senderId,
                NotificationType.APPROVAL_REJECTED,
                title + " 결재가 반려되었습니다. 사유: " + reason,
                requestId);

        //        참조자 (요청자와 중복 제외)
        if (viewers != null) {
            viewers.stream()
                    .filter(v -> v.getViewerType() == ViewerType.CC)
                    .filter(v -> notified.add(v.getViewerMemberId()))
                    .forEach(v -> publish(
                            v.getViewerMemberId(), senderId,
                            NotificationType.APPROVAL_REJECTED,
                            title + " 참조 중인 결재가 반려되었습니다. 사유: " + reason,
                            requestId));
        }
    }

    //    회수 (WAIT → CANCELED)
    //    첫 결재자/참조자: APPROVAL_CANCELED (공람자 알림 없음)
    public void notifyCanceled(ApprovalRequest request,
                               Approval firstApproval,
                               List<ApprovalViewer> viewers) {
        String title = title(request);
        UUID requesterId = request.getMemberId();
        UUID requestId = request.getRequestId();

        Set<UUID> notified = new HashSet<>();
        notified.add(requesterId); // 요청자 본인은 알림 제외

        //        첫 결재자
        if (firstApproval != null) {
            UUID firstApproverId = firstApproval.getApproverMemberId();
            if (notified.add(firstApproverId)) {
                publish(firstApproverId, requesterId,
                        NotificationType.APPROVAL_CANCELED,
                        title + " 결재 요청이 회수되었습니다.",
                        requestId);
            }
        }

        //        참조자
        if (viewers != null) {
            viewers.stream()
                    .filter(v -> v.getViewerType() == ViewerType.CC)
                    .filter(v -> notified.add(v.getViewerMemberId()))
                    .forEach(v -> publish(
                            v.getViewerMemberId(), requesterId,
                            NotificationType.APPROVAL_CANCELED,
                            title + " 참조 중인 결재가 회수되었습니다.",
                            requestId));
        }
    }

    //    최종 승인 시 수신자 중복 제거
    //    우선순위: 요청자 > 참조자(CC) > 공람자(CIRCULATION)
    private List<Receiver> resolveCompletionReceivers(UUID requesterId,
                                                      List<ApprovalViewer> viewers) {
        List<Receiver> receivers = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        //        요청자
        receivers.add(new Receiver(requesterId, Role.REQUESTER));
        seen.add(requesterId);

        if (viewers == null) return receivers;

        //        참조자
        viewers.stream()
                .filter(v -> v.getViewerType() == ViewerType.CC)
                .filter(v -> seen.add(v.getViewerMemberId()))
                .forEach(v -> receivers.add(
                        new Receiver(v.getViewerMemberId(), Role.REFERENCE)));

        //        공람자
        viewers.stream()
                .filter(v -> v.getViewerType() == ViewerType.CIRCULATION)
                .filter(v -> seen.add(v.getViewerMemberId()))
                .forEach(v -> receivers.add(
                        new Receiver(v.getViewerMemberId(), Role.VIEWER)));

        return receivers;
    }

    private void publish(UUID receiverId, UUID senderId,
                         NotificationType type, String content, UUID targetId) {
        if (receiverId == null) {
            log.warn("알림 수신자 없음 — skip. type={}, targetId={}", type, targetId);
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

    /**
     * 결재자에게 활성 위임이 있으면 수임자에게도 알림 전송
     */
    private void notifyProxyIfExists(UUID approverMemberId, UUID senderId,
                                     String title, UUID requestId) {
        absenceProxyRepository
                .findActiveProxyByMemberId(approverMemberId, LocalDateTime.now())
                .ifPresent(proxy -> {
                    publish(proxy.getSubstituteId(), senderId,
                            NotificationType.APPROVAL_REQUESTED,
                            title + " 대결 결재 요청이 도착했습니다.",
                            requestId);
                });
    }

    private String title(ApprovalRequest request){
        return request.getApprovalDocument().getDocumentName();
    }

    private enum Role { REQUESTER, REFERENCE, VIEWER }

    private static class Receiver {
        final UUID memberId;
        final Role role;

        Receiver(UUID memberId, Role role) {
            this.memberId = memberId;
            this.role = role;
        }
    }

    // 발송 가능 알림 (최종 승인 시 기안자에게)
    public void notifyOfficialReadyToSend(ApprovalRequest request) {
        String title = title(request);
        publish(request.getMemberId(), null,
                NotificationType.APPROVAL_APPROVED,
                title + " 공문이 승인되었습니다. 발송해주세요.",
                request.getRequestId());
    }

    // 공문 미발송 취소 알림 (결재자 + 참조자에게)
    public void notifyOfficialCanceled(ApprovalRequest request,
                                       List<Approval> approvals,
                                       List<ApprovalViewer> viewers) {
        String title = title(request);
        UUID requesterId = request.getMemberId();
        UUID requestId = request.getRequestId();

        Set<UUID> notified = new HashSet<>();
        notified.add(requesterId); // 기안자 본인 제외

        // 결재자에게 알림
        for (Approval a : approvals) {
            if (notified.add(a.getApproverMemberId())) {
                publish(a.getApproverMemberId(), requesterId,
                        NotificationType.APPROVAL_CANCELED,
                        title + " 공문이 발송 취소되었습니다.",
                        requestId);
            }
        }

        // 참조자에게 알림
        if (viewers != null) {
            viewers.stream()
                    .filter(v -> v.getViewerType() == ViewerType.CC)
                    .filter(v -> notified.add(v.getViewerMemberId()))
                    .forEach(v -> publish(
                            v.getViewerMemberId(), requesterId,
                            NotificationType.APPROVAL_CANCELED,
                            title + " 참조 중인 공문이 발송 취소되었습니다.",
                            requestId));
        }
    }


}
