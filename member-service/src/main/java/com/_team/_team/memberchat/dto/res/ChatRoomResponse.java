package com._team._team.memberchat.dto.res;

import com._team._team.memberchat.domain.ChatRoom;
import com._team._team.memberchat.domain.enums.ChatRoomType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 방 목록 API 응답.
 *
 * 읽음/미리보기 관련 필드:
 *  - {@code unreadCount}                 : 내가 아직 안 읽은 메시지 수 (내가 보낸 건 제외)
 *  - {@code lastMessageId}               : 방의 최신 메시지 id (없으면 null)
 *  - {@code lastMessagePreview}          : 미리보기 텍스트 (삭제 메시지는 "삭제된 메시지입니다.")
 *  - {@code lastMessageSenderId}         : 최신 메시지 발신자 id
 *  - {@code lastMessageAt}               : 최신 메시지 생성 시각
 *  - {@code myLastReadMessageId}         : 내 lastRead (방 진입 시 프론트가 참조)
 *  - {@code otherPartyLastReadMessageId} : 1:1 전용. 상대방 lastRead (그룹은 null)
 */
public record ChatRoomResponse(
        Long id,
        String name,
        ChatRoomType type,
        UUID companyId,
        String imageUrl,
        boolean deleted,
        LocalDateTime createdAt,
        long participantCount,
        long unreadCount,
        Long lastMessageId,
        String lastMessagePreview,
        UUID lastMessageSenderId,
        LocalDateTime lastMessageAt,
        Long myLastReadMessageId,
        Long otherPartyLastReadMessageId,
        /** 1:1 방 전용 — 상대방 프로필(이름·프로필·직급·소속). 그룹은 null. */
        UUID otherMemberId,
        String otherMemberName,
        String otherMemberProfileUrl,
        String otherMemberJobTitleName,
        String otherMemberJobGradeName,
        String otherMemberOrganizationName
) {
    public static ChatRoomResponse from(ChatRoom r,
                                        String displayName,
                                        long participantCount,
                                        long unreadCount,
                                        Long lastMessageId,
                                        String lastMessagePreview,
                                        UUID lastMessageSenderId,
                                        LocalDateTime lastMessageAt,
                                        Long myLastReadMessageId,
                                        Long otherPartyLastReadMessageId,
                                        UUID otherMemberId,
                                        ChatSenderView otherMemberView) {
        return new ChatRoomResponse(
                r.getId(), displayName, r.getType(), r.getCompanyId(),
                r.getImageUrl(), r.isDeleted(),
                r.getCreatedAt(), participantCount,
                unreadCount,
                lastMessageId, lastMessagePreview, lastMessageSenderId, lastMessageAt,
                myLastReadMessageId, otherPartyLastReadMessageId,
                otherMemberId,
                otherMemberView == null ? null : otherMemberView.name(),
                otherMemberView == null ? null : otherMemberView.profileUrl(),
                otherMemberView == null ? null : otherMemberView.jobTitleName(),
                otherMemberView == null ? null : otherMemberView.jobGradeName(),
                otherMemberView == null ? null : otherMemberView.organizationName());
    }

    public static ChatRoomResponse from(ChatRoom r,
                                        long participantCount,
                                        long unreadCount,
                                        Long lastMessageId,
                                        String lastMessagePreview,
                                        UUID lastMessageSenderId,
                                        LocalDateTime lastMessageAt,
                                        Long myLastReadMessageId,
                                        Long otherPartyLastReadMessageId,
                                        UUID otherMemberId,
                                        ChatSenderView otherMemberView) {
        return from(r, r.getName(), participantCount, unreadCount, lastMessageId, lastMessagePreview,
                lastMessageSenderId, lastMessageAt, myLastReadMessageId, otherPartyLastReadMessageId,
                otherMemberId, otherMemberView);
    }

    /** 기존 호환용 (상대 프로필 없이). */
    public static ChatRoomResponse from(ChatRoom r,
                                        long participantCount,
                                        long unreadCount,
                                        Long lastMessageId,
                                        String lastMessagePreview,
                                        UUID lastMessageSenderId,
                                        LocalDateTime lastMessageAt,
                                        Long myLastReadMessageId,
                                        Long otherPartyLastReadMessageId) {
        return from(r, participantCount, unreadCount, lastMessageId, lastMessagePreview,
                lastMessageSenderId, lastMessageAt, myLastReadMessageId, otherPartyLastReadMessageId,
                null, null);
    }

    /** legacy 호환용 (unread/preview 없이 기본값). */
    public static ChatRoomResponse from(ChatRoom r, long participantCount) {
        return from(r, participantCount, 0L, null, null, null, null, null, null, null, null);
    }
}
