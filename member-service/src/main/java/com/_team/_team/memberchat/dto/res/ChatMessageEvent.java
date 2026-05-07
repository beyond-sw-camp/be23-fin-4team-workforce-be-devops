package com._team._team.memberchat.dto.res;

import com._team._team.memberchat.domain.ChatMessage;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 구독자에게 발행되는 이벤트. Redis Pub/Sub 및 /mc/topic/room/{roomId} 로 전달.
 */
public record ChatMessageEvent(
        String eventType,           // MESSAGE_CREATED | MESSAGE_EDITED | MESSAGE_DELETED
        Long roomId,
        Long messageId,
        String clientMessageId,
        UUID senderId,
        String type,                // MessageType
        String content,
        boolean deleted,
        boolean edited,
        LocalDateTime createdAt,
        LocalDateTime editedAt,
        Long replyToId
) {
    public static ChatMessageEvent created(ChatMessage m) {
        return of("MESSAGE_CREATED", m);
    }
    public static ChatMessageEvent edited(ChatMessage m) {
        return of("MESSAGE_EDITED", m);
    }
    public static ChatMessageEvent deleted(ChatMessage m) {
        return of("MESSAGE_DELETED", m);
    }
    private static ChatMessageEvent of(String et, ChatMessage m) {
        return new ChatMessageEvent(
                et,
                m.getChatRoom().getId(),
                m.getId(),
                m.getClientMessageId(),
                m.getSenderId(),
                m.getType().name(),
                m.isDeleted() ? "삭제된 메시지입니다." : m.getContent(),
                m.isDeleted(),
                m.isEdited(),
                m.getCreatedAt(),
                m.getEditedAt(),
                m.getReplyToId()
        );
    }
}
