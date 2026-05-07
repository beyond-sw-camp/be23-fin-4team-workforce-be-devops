package com._team._team.notification.dto;

import com._team._team.notification.NotificationType;
import com._team._team.notification.domain.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResDto {

    private UUID notificationId;
    private NotificationType notificationType;
    private String content;
    private UUID targetId;
    private String targetType;
    private String isRead;
    private LocalDateTime createdAt;
    private String delYn;

    public static NotificationResDto fromEntity(Notification notification) {
        return NotificationResDto.builder()
                .notificationId(notification.getNotificationId())
                .notificationType(notification.getNotificationType())
                .content(notification.getContent())
                .targetId(notification.getTargetId())
                .targetType(notification.getTargetType())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .delYn(notification.getDelYn())
                .build();
    }
}