package com._team._team.notification.service;

import com._team._team.dto.BusinessException;
import com._team._team.member.domain.Member;
import com._team._team.member.repository.MemberRepository;
import com._team._team.notification.domain.Notification;
import com._team._team.notification.dto.NotificationResDto;
import com._team._team.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    // 알림 목록 조회
    @Transactional(readOnly = true)
    public List<NotificationResDto> getNotificationList(UUID memberId) {
        Member member = getMember(memberId);

        return notificationRepository
                .findByMemberAndDelYnOrderByCreatedAtDesc(member, "N")
                .stream()
                .map(NotificationResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 읽지 않은 알림 수 조회
    @Transactional(readOnly = true)
    public int getUnreadCount(UUID memberId) {
        Member member = getMember(memberId);

        return notificationRepository.countByMemberAndIsReadAndDelYn(member, "N", "N");
    }

    // 알림 읽음 처리
    public void readNotification(UUID memberId, UUID notificationId) {
        Notification notification = getNotification(notificationId);
        validateOwner(notification, memberId, "본인의 알림만 읽음 처리할 수 있습니다.");

        notification.read();
    }

    // 알림 전체 읽음 처리
    public void readAllNotification(UUID memberId) {
        Member member = getMember(memberId);

        notificationRepository
                .findByMemberAndIsReadAndDelYn(member, "N", "N")
                .forEach(Notification::read);
    }

    public void sendMissedNotifications(UUID memberId,
                                        String lastEventId,
                                        SseEmitter emitter) {
        // Last-Event-ID가 없으면 누락 알림을 재전송하지 않는다.
        if (lastEventId == null || lastEventId.isEmpty()) {
            return;
        }

        Member member = getMember(memberId);
        LocalDateTime lastEventTime = LocalDateTime.parse(lastEventId);

        notificationRepository
                .findByMemberAndCreatedAtAfterOrderByCreatedAtAsc(
                        member, lastEventTime)
                .forEach(notification -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .id(notification.getCreatedAt().toString())
                                .name("notification")
                                .data(NotificationResDto.fromEntity(notification)));
                    } catch (Exception e) {
                        log.error("누락 알림 전송 실패: {}", e.getMessage());
                    }
                });
    }

    // 알림 삭제
    public void deleteNotification(UUID memberId, UUID notificationId) {
        Notification notification = getNotification(notificationId);
        validateOwner(notification, memberId, "본인의 알림만 삭제할 수 있습니다.");

        notification.delete();
    }

    // 알림 전체 삭제
    public void deleteAllNotification(UUID memberId) {
        Member member = getMember(memberId);

        notificationRepository
                .findByMemberAndDelYnOrderByCreatedAtDesc(member, "N")
                .forEach(Notification::delete);
    }

    private Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));
    }

    private Notification getNotification(UUID notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."));
    }

    private void validateOwner(Notification notification, UUID memberId, String message) {
        if (!notification.getMember().getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, message);
        }
    }
}
