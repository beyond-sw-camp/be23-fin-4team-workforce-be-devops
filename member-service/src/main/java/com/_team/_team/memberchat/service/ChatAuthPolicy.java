package com._team._team.memberchat.service;

import com._team._team.memberchat.config.ChatStompHandler.AuthPrincipal;
import com._team._team.memberchat.domain.ChatParticipant;
import com._team._team.memberchat.domain.ChatRoom;
import com._team._team.memberchat.domain.enums.MessageType;
import com._team._team.memberchat.error.ChatErrorCode;
import com._team._team.memberchat.error.ChatException;
import com._team._team.memberchat.repository.ChatParticipantRepository;
import com._team._team.memberchat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Chat 도메인의 인가 로직을 중앙화.
 * STOMP 와 REST 양쪽에서 동일한 정책을 사용한다.
 */
@Component
@RequiredArgsConstructor
public class ChatAuthPolicy {

    private final ChatRoomRepository roomRepo;
    private final ChatParticipantRepository participantRepo;

    public void canSubscribe(AuthPrincipal principal, long roomId) {
        if (principal == null) throw new ChatException(ChatErrorCode.INVALID_JWT);
        ChatRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.ROOM_NOT_FOUND));
        if (room.isDeleted()) throw new ChatException(ChatErrorCode.ROOM_DELETED);

        // HR_ADMIN/AUDITOR 는 감사 목적 전체 열람
        if (principal.isHrAdminOrAuditor()) return;

        UUID memberId = principal.userId();
        ChatParticipant p = participantRepo.findByChatRoomIdAndMemberId(roomId, memberId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.NOT_PARTICIPANT));
        if (!p.isJoined()) throw new ChatException(ChatErrorCode.NOT_PARTICIPANT);
    }

    public ChatParticipant requireActiveParticipant(UUID memberId, long roomId) {
        ChatParticipant p = participantRepo.findByChatRoomIdAndMemberId(roomId, memberId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.NOT_PARTICIPANT));
        if (!p.isJoined()) throw new ChatException(ChatErrorCode.NOT_PARTICIPANT);
        return p;
    }

    public void canSendMessage(UUID senderId, long roomId, MessageType type) {
        ChatParticipant p = requireActiveParticipant(senderId, roomId);
        if (type == MessageType.NOTICE && !p.canSendNotice()) {
            throw new ChatException(ChatErrorCode.NOTICE_FORBIDDEN);
        }
    }
}
