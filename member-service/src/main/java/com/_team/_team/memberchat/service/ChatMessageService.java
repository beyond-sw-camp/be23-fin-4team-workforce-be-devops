package com._team._team.memberchat.service;

import com._team._team.memberchat.domain.*;
import com._team._team.memberchat.domain.enums.ChatParticipantStatus;
import com._team._team.memberchat.dto.req.ChatSendRequest;
import com._team._team.memberchat.dto.res.ChatMessageEvent;
import com._team._team.memberchat.dto.res.ChatMessageResponse;
import com._team._team.memberchat.dto.res.ChatSenderView;
import com._team._team.memberchat.error.ChatErrorCode;
import com._team._team.memberchat.error.ChatException;
import com._team._team.memberchat.repository.ChatMessageRepository;
import com._team._team.memberchat.repository.ChatParticipantRepository;
import com._team._team.memberchat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomRepository roomRepo;
    private final ChatMessageRepository messageRepo;
    private final ChatParticipantRepository participantRepo;
    private final ChatAuthPolicy authPolicy;
    private final RedisChatPubSubService pubsub;
    private final ChatMessageSenderEnricher senderEnricher;

    @Transactional
    public ChatMessageEvent saveAndPublish(UUID senderId, long roomId, ChatSendRequest req) {
        authPolicy.canSendMessage(senderId, roomId, req.type());

        if (req.clientMessageId() != null) {
            messageRepo.findByClientMessageId(req.clientMessageId())
                    .ifPresent(existing -> { throw new ChatException(ChatErrorCode.DUPLICATE); });
        }

        ChatRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.ROOM_NOT_FOUND));
        if (room.isDeleted()) throw new ChatException(ChatErrorCode.ROOM_DELETED);

        ChatMessage saved = messageRepo.save(ChatMessage.builder()
                .chatRoom(room)
                .senderId(senderId)
                .type(req.type())
                .content(req.content())
                .clientMessageId(req.clientMessageId())
                .replyToId(req.replyToId())
                .build());

        ChatMessageEvent event = ChatMessageEvent.created(saved);
        pubsub.publish(roomId, event);
        return event;
    }

    @Transactional
    public ChatMessageEvent editMessage(UUID actorId, long messageId, String newContent) {
        ChatMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (m.isDeleted()) throw new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND);
        if (!m.getSenderId().equals(actorId)) throw new ChatException(ChatErrorCode.EDIT_FORBIDDEN);
        if (m.isOlderThan(Duration.ofHours(24))) throw new ChatException(ChatErrorCode.EDIT_WINDOW_EXPIRED);

        m.updateContent(newContent);

        ChatMessageEvent event = ChatMessageEvent.edited(m);
        pubsub.publish(m.getChatRoom().getId(), event);
        return event;
    }

    @Transactional
    public ChatMessageEvent deleteMessage(UUID actorId, long messageId, boolean adminOverride) {
        ChatMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (m.isDeleted()) throw new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND);
        if (!adminOverride && !m.getSenderId().equals(actorId)) {
            throw new ChatException(ChatErrorCode.EDIT_FORBIDDEN);
        }

        m.softDelete();

        ChatMessageEvent event = ChatMessageEvent.deleted(m);
        pubsub.publish(m.getChatRoom().getId(), event);
        return event;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(UUID memberId, long roomId, Long cursor, int size) {
        authPolicy.requireActiveParticipant(memberId, roomId);
        var room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.ROOM_NOT_FOUND));
        List<ChatMessage> list = messageRepo.pageBefore(roomId, cursor, PageRequest.of(0, Math.min(size, 200)));
        return toMessageResponses(room.getCompanyId(), roomId, list);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> sync(UUID memberId, long roomId, Long lastSeenMessageId, int size) {
        authPolicy.requireActiveParticipant(memberId, roomId);
        var room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.ROOM_NOT_FOUND));
        long lastSeen = lastSeenMessageId == null ? 0L : lastSeenMessageId;
        List<ChatMessage> list = messageRepo.fetchAfter(roomId, lastSeen, PageRequest.of(0, Math.min(size, 500)));
        return toMessageResponses(room.getCompanyId(), roomId, list);
    }

    private List<ChatMessageResponse> toMessageResponses(UUID companyId, long roomId, List<ChatMessage> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        // 1) 보낸이 표시정보 벌크 로드
        Set<UUID> senderIds = list.stream().map(ChatMessage::getSenderId).collect(Collectors.toCollection(HashSet::new));
        Map<UUID, ChatSenderView> views = senderEnricher.loadSenders(companyId, senderIds);

        // 2) 방 참여자 lastReadMessageId 벌크 로드 → 각 메시지의 readerCount 계산 (N+1 방지)
        List<ChatParticipant> joined = participantRepo.findByChatRoomId(roomId).stream()
                .filter(p -> p.getStatus() == ChatParticipantStatus.JOINED)
                .toList();

        return list.stream()
                .map(m -> ChatMessageResponse.from(m, roomId,
                        views.get(m.getSenderId()),
                        computeReaderCount(joined, m)))
                .toList();
    }

    /**
     * "이 메시지를 읽은 다른 참여자 수".
     *  - 보낸 사람 본인은 제외 (자기 메시지는 자동 읽음으로 간주하되 카운트에 포함하지 않는다)
     *  - JOINED 참여자 중 lastReadMessageId ≥ m.id 인 사람 수
     */
    private long computeReaderCount(List<ChatParticipant> joined, ChatMessage m) {
        long count = 0L;
        for (ChatParticipant p : joined) {
            if (p.getMemberId().equals(m.getSenderId())) continue;
            Long lr = p.getLastReadMessageId();
            if (lr != null && lr >= m.getId()) count++;
        }
        return count;
    }
}
