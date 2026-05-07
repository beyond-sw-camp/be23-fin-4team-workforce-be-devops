package com._team._team.memberchat.dto.res;

import com._team._team.memberchat.domain.ChatMessage;
import com._team._team.memberchat.domain.ChatMessageImage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 히스토리/싱크 응답용 DTO.
 * ChatMessage 엔티티를 그대로 직렬화하면 chatRoom ↔ participants 양방향 참조로 인해
 * Jackson 이 무한 재귀를 돌기 때문에 DTO 로 변환해 내려준다.
 *
 * 읽음 관련 필드:
 *  - {@code readerCount}: 이 메시지를 읽은 참여자 수(보낸 사람 자신은 제외).
 *                         1:1 방이면 0/1, 그룹이면 0..N-1. 프론트가 "안 읽은 수 = (참여자수-1) - readerCount" 로 표기.
 */
public record ChatMessageResponse(
        Long id,
        Long roomId,
        UUID senderId,
        String senderName,
        String senderProfileUrl,
        String senderJobTitleName,
        String senderJobGradeName,
        String senderOrganizationName,
        String type,
        String content,
        String clientMessageId,
        Long replyToId,
        boolean deleted,
        boolean edited,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime editedAt,
        long readerCount,
        List<Attachment> attachments
) {
    public record Attachment(
            Long id,
            String url,
            String mimeType,
            Long sizeBytes,
            String scanStatus
    ) {
        public static Attachment from(ChatMessageImage img) {
            return new Attachment(
                    img.getId(),
                    img.getUrl(),
                    img.getMimeType(),
                    img.getSizeBytes(),
                    img.getScanStatus()
            );
        }
    }

    /** 히스토리/동기화: 방 ID·발신자 표시 정보·읽은 사람 수를 서버에서 채운다. */
    public static ChatMessageResponse from(ChatMessage m, long roomId, ChatSenderView sender, long readerCount) {
        List<Attachment> atts = m.getAttachments() == null
                ? List.of()
                : m.getAttachments().stream().map(Attachment::from).toList();
        return new ChatMessageResponse(
                m.getId(),
                roomId,
                m.getSenderId(),
                sender != null ? sender.name() : null,
                sender != null ? sender.profileUrl() : null,
                sender != null ? sender.jobTitleName() : null,
                sender != null ? sender.jobGradeName() : null,
                sender != null ? sender.organizationName() : null,
                m.getType() != null ? m.getType().name() : null,
                m.isDeleted() ? "삭제된 메시지입니다." : m.getContent(),
                m.getClientMessageId(),
                m.getReplyToId(),
                m.isDeleted(),
                m.isEdited(),
                m.getCreatedAt(),
                m.getUpdatedAt(),
                m.getEditedAt(),
                readerCount,
                atts
        );
    }

    /** 읽음 정보가 필요 없는 경로(이벤트 페이로드 등)용 오버로드. */
    public static ChatMessageResponse from(ChatMessage m, long roomId, ChatSenderView sender) {
        return from(m, roomId, sender, 0L);
    }
}
