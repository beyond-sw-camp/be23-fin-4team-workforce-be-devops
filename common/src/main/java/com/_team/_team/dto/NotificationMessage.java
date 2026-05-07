package com._team._team.dto;

import com._team._team.notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationMessage {

    private UUID receiverId;        // 받는 사람 memberId
    private UUID senderId;          // 보내는 사람 memberId
    private NotificationType notificationType;
    private String content;
    private UUID targetId;          // 관련 대상 ID (결재ID, 근태ID 등)
    private String targetType;      // 관련 대상 타입 (APPROVAL, ATTENDANCE 등)
    private String eventId;
}