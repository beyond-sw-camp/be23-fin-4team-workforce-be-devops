package com._team._team.notification.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.notification.service.NotificationService;
import com._team._team.notification.sse.SseEmitterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final SseEmitterManager sseEmitterManager;
    private final NotificationService notificationService;

    @Autowired
    public NotificationController(SseEmitterManager sseEmitterManager, NotificationService notificationService) {
        this.sseEmitterManager = sseEmitterManager;
        this.notificationService = notificationService;
    }

    // SSE 구독
    @GetMapping(value = "/subscribe",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader(value = "Last-Event-ID",
                    required = false, defaultValue = "") String lastEventId) {

        return sseEmitterManager.add(memberId, lastEventId);
    }

    // 알림 목록 조회
    @GetMapping("/list")
    public ResponseEntity<?> getNotificationList(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        notificationService.getNotificationList(memberId),
                        "알림 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 읽지 않은 알림 수 조회
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        notificationService.getUnreadCount(memberId),
                        "읽지 않은 알림 수 조회 성공"),
                HttpStatus.OK
        );
    }

    // 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<?> readNotification(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID notificationId) {
        notificationService.readNotification(memberId, notificationId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "알림 읽음 처리 성공"),
                HttpStatus.OK
        );
    }

    // 알림 전체 읽음 처리
    @PatchMapping("/read-all")
    public ResponseEntity<?> readAllNotification(
            @RequestHeader("X-User-UUID") UUID memberId) {
        notificationService.readAllNotification(memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "알림 전체 읽음 처리 성공"),
                HttpStatus.OK
        );
    }

    // 알림 삭제
    @PatchMapping("/{notificationId}/delete")
    public ResponseEntity<?> deleteNotification(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID notificationId) {
        notificationService.deleteNotification(memberId, notificationId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "알림 삭제 성공"),
                HttpStatus.OK
        );
    }

    // 알림 전체 삭제
    @PatchMapping("/delete-all")
    public ResponseEntity<?> deleteAllNotification(
            @RequestHeader("X-User-UUID") UUID memberId) {
        notificationService.deleteAllNotification(memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "알림 전체 삭제 성공"),
                HttpStatus.OK
        );
    }
}
