package com._team._team.attendance.service;

import com._team._team.attendance.domain.OvertimeRequest;
import com._team._team.attendance.repository.OvertimePolicyRepository;
import com._team._team.attendance.repository.OvertimeRequestRepository;
import com._team._team.dto.NotificationMessage;
import com._team._team.event.OvertimeApprovalEvent;
import com._team._team.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 초과근무 결재 이벤트 처리 서비스
 * approval-service에서 날아온 승인/반려/취소 이벤트를 받아 본인에게 실시간 알림 발행
 */
@Slf4j
@Service
public class OvertimeApprovalService {

    private static final String TARGET_TYPE = "OVERTIME_APPROVAL";
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationEventPublisher eventPublisher;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final OvertimePolicyRepository overtimePolicyRepository;

    @Autowired
    public OvertimeApprovalService(ApplicationEventPublisher eventPublisher,
                                   OvertimeRequestRepository overtimeRequestRepository, OvertimePolicyRepository overtimePolicyRepository) {
        this.eventPublisher = eventPublisher;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.overtimePolicyRepository = overtimePolicyRepository;
    }

    // 승인 이벤트 - 본인에게 승인 알림
    @Transactional
    public void applyApproval(OvertimeApprovalEvent event) {
        log.info("[Overtime] approved. memberId={}, {} ~ {}, workType={}",
                event.getMemberId(), event.getStartAt(), event.getEndAt(), event.getWorkType());

        // OvertimeRequest 상태 전이
        overtimeRequestRepository.findByApprovalRequestId(event.getRequestId())
                .ifPresentOrElse(
                        request -> {
                            int rawMinutes = calculateMinutes(event);
                            int rounded = applyRounding(request, rawMinutes);
                            request.approve(null, rounded, LocalDateTime.now());
                        },
                        () -> log.warn("[Overtime] OvertimeRequest not found. requestId={}",
                                event.getRequestId())
                );

        // 본인 알림
        String content = String.format(
                "초과근무(%s) 결재가 승인되었습니다. (%s ~ %s)",
                nullSafe(event.getWorkType()),
                formatDt(event.getStartAt()),
                formatDt(event.getEndAt()));
        publish(event, NotificationType.APPROVAL_APPROVED, content);
    }

    // 반려·취소 이벤트 처리
    @Transactional
    public void applyRejection(OvertimeApprovalEvent event) {
        log.info("[Overtime] rejected/canceled. memberId={}, requestId={}, action={}",
                event.getMemberId(), event.getRequestId(), event.getAction());

        String note = event.getAction() == OvertimeApprovalEvent.Action.CANCEL
                ? "결재 취소됨"
                : "결재 반려됨";

        // OvertimeRequest 상태 전이
        overtimeRequestRepository.findByApprovalRequestId(event.getRequestId())
                .ifPresentOrElse(
                        request -> request.reject(null, LocalDateTime.now(), note),
                        () -> log.warn("[Overtime] OvertimeRequest not found. requestId={}",
                                event.getRequestId())
                );

        // 본인 알림
        String content;
        if (event.getAction() == OvertimeApprovalEvent.Action.CANCEL) {
            content = String.format(
                    "초과근무(%s) 결재가 취소되었습니다. (%s ~ %s)",
                    nullSafe(event.getWorkType()),
                    formatDt(event.getStartAt()),
                    formatDt(event.getEndAt()));
        } else {
            content = String.format(
                    "초과근무(%s) 결재가 반려되었습니다. 정규 퇴근 시각에 맞춰 퇴근 처리해주세요. (%s ~ %s)",
                    nullSafe(event.getWorkType()),
                    formatDt(event.getStartAt()),
                    formatDt(event.getEndAt()));
        }
        publish(event, NotificationType.APPROVAL_REJECTED, content);
    }

    // 승인 시간 범위로 approvedMinutes 계산
    private int calculateMinutes(OvertimeApprovalEvent event) {
        if (event.getStartAt() == null || event.getEndAt() == null) {
            return 0;
        }
        return (int) Duration.between(event.getStartAt(), event.getEndAt()).toMinutes();
    }

    // 본인(receiver=memberId)에게 시스템 발송 (senderId=null)
    private void publish(OvertimeApprovalEvent event, NotificationType type, String content) {
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(event.getMemberId())
                .senderId(null)
                .notificationType(type)
                .content(content)
                .targetId(event.getRequestId())
                .targetType(TARGET_TYPE)
                .build());
    }

    private static String formatDt(LocalDateTime dt) {
        return dt == null ? "-" : dt.format(DT_FMT);
    }

    private static String nullSafe(String s) {
        return s == null ? "-" : s;
    }

    // 해당 건의 회사, 일자 기준 정책 조회 후 FLOOR 라운딩 적용
    private int applyRounding(OvertimeRequest request, int minutes) {
        if (minutes <= 0) return 0;
        int unit = overtimePolicyRepository
                .findEffective(request.getCompanyId(), request.getTargetDate())
                .orElseThrow(() -> new IllegalStateException(
                        "OvertimePolicy not found. companyId=" + request.getCompanyId()))
                .getOvertimeFloorMinutes();
        return (minutes / unit) * unit;
    }
}