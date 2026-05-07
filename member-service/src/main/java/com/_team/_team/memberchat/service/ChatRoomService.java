package com._team._team.memberchat.service;

import com._team._team.memberchat.domain.*;
import com._team._team.memberchat.domain.enums.ChatParticipantStatus;
import com._team._team.memberchat.domain.enums.ChatRoomRole;
import com._team._team.memberchat.domain.enums.ChatRoomType;
import com._team._team.memberchat.domain.enums.MessageType;
import com._team._team.memberchat.dto.req.CreateDirectRoomRequest;
import com._team._team.memberchat.dto.req.CreateGroupRoomRequest;
import com._team._team.memberchat.dto.res.ChatMessageEvent;
import com._team._team.memberchat.dto.res.ChatParticipantResponse;
import com._team._team.memberchat.dto.res.ChatRoomResponse;
import com._team._team.memberchat.dto.res.ChatSenderView;
import com._team._team.memberchat.error.ChatErrorCode;
import com._team._team.memberchat.error.ChatException;
import com._team._team.memberchat.repository.ChatMessageRepository;
import com._team._team.memberchat.repository.ChatParticipantRepository;
import com._team._team.memberchat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepo;
    private final ChatParticipantRepository participantRepo;
    private final ChatMessageRepository messageRepo;
    private final ChatAuthPolicy authPolicy;
    private final ChatMessageSenderEnricher senderEnricher;
    private final RedisChatPubSubService pubsub;

    @Transactional
    public ChatRoomResponse getOrCreateDirectRoom(UUID me, UUID companyId, CreateDirectRoomRequest req) {
        if (me.equals(req.otherMemberId())) {
            throw new ChatException(ChatErrorCode.VALIDATION_ERROR, "자기 자신과의 1:1 방은 만들 수 없습니다.");
        }
        return roomRepo.findDirectRoomOf(companyId, me, req.otherMemberId(), ChatRoomType.DIRECT)
                .map(existing -> {
                    // HIDDEN 참여자 → 필요 시 복구 (채팅 재개)
                    participantRepo.findByChatRoomIdAndMemberId(existing.getId(), me)
                            .ifPresent(p -> { if (p.getStatus() == ChatParticipantStatus.HIDDEN) p.restoreFromHidden(null); });
                    return toResponse(existing, me);
                })
                .orElseGet(() -> {
                    ChatRoom room = roomRepo.save(ChatRoom.builder()
                            .name("direct") // 클라이언트가 상대 이름으로 렌더
                            .type(ChatRoomType.DIRECT)
                            .companyId(companyId)
                            .createdBy(me)
                            .build());
                    persistParticipant(room, me, ChatRoomRole.MEMBER);
                    persistParticipant(room, req.otherMemberId(), ChatRoomRole.MEMBER);
                    return toResponse(room, me);
                });
    }

    @Transactional
    public ChatRoomResponse createGroup(UUID me, UUID companyId, CreateGroupRoomRequest req) {
        ChatRoom room = roomRepo.save(ChatRoom.builder()
                .name(req.name())
                .type(ChatRoomType.GROUP)
                .companyId(companyId)
                .createdBy(me)
                .build());

        persistParticipant(room, me, ChatRoomRole.MEMBER);
        req.memberIds().stream()
                .distinct()
                .filter(id -> !id.equals(me))
                .forEach(id -> persistParticipant(room, id, ChatRoomRole.MEMBER));

        return toResponse(room, me);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> myRooms(UUID me) {
        List<ChatRoom> rooms = roomRepo.findMyRooms(me);

        // DIRECT 방의 "상대 멤버" 들을 한 번에 모아 회사별로 batch enrichment.
        // (room → otherMemberId) 매핑을 만든 뒤, 동일 companyId 로 묶어 loadSenders 1회 호출.
        Map<Long, UUID> directOtherByRoomId = new java.util.HashMap<>();
        Map<UUID, Set<UUID>> idsByCompany = new java.util.HashMap<>();
        for (ChatRoom room : rooms) {
            if (room.getType() != ChatRoomType.DIRECT) continue;
            UUID other = participantRepo.findByChatRoomId(room.getId()).stream()
                    .filter(p -> p.getStatus() == ChatParticipantStatus.JOINED || p.getStatus() == ChatParticipantStatus.HIDDEN)
                    .map(ChatParticipant::getMemberId)
                    .filter(id -> !id.equals(me))
                    .findFirst()
                    .orElse(null);
            if (other == null) continue;
            directOtherByRoomId.put(room.getId(), other);
            idsByCompany.computeIfAbsent(room.getCompanyId(), k -> new HashSet<>()).add(other);
        }
        Map<UUID, ChatSenderView> viewsByMemberId = new java.util.HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> e : idsByCompany.entrySet()) {
            viewsByMemberId.putAll(senderEnricher.loadSenders(e.getKey(), e.getValue()));
        }

        return rooms.stream()
                .map(r -> toResponse(r, me, directOtherByRoomId.get(r.getId()), viewsByMemberId))
                .toList();
    }

    /**
     * 방 참여자 목록 조회 — 이름·프로필·직급·부서·역할 포함.
     * 방 참여자(JOINED)만 조회 가능. 현재 방에 남아있는(JOINED) 참여자만 응답에 포함한다.
     * 정렬: OWNER → MODERATOR → MEMBER, 그다음 이름(가나다) 순.
     */
    @Transactional(readOnly = true)
    public List<ChatParticipantResponse> listParticipants(UUID me, long roomId) {
        // 인가: 방 활성 참여자만 접근
        authPolicy.requireActiveParticipant(me, roomId);

        ChatRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.ROOM_NOT_FOUND));
        if (room.isDeleted()) throw new ChatException(ChatErrorCode.ROOM_DELETED);

        List<ChatParticipant> all = participantRepo.findByChatRoomId(roomId);
        List<ChatParticipant> joined = all.stream()
                .filter(p -> p.getStatus() == ChatParticipantStatus.JOINED)
                .toList();

        Set<UUID> memberIds = joined.stream()
                .map(ChatParticipant::getMemberId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Map<UUID, ChatSenderView> views = senderEnricher.loadSenders(room.getCompanyId(), memberIds);

        return joined.stream()
                .map(p -> ChatParticipantResponse.from(p, views.get(p.getMemberId())))
                .sorted(
                        Comparator
                                .comparingInt((ChatParticipantResponse r) -> roleOrder(r.role()))
                                .thenComparing(r -> r.name() == null ? "" : r.name())
                )
                .toList();
    }

    private static int roleOrder(ChatRoomRole role) {
        if (role == ChatRoomRole.OWNER) return 0;
        if (role == ChatRoomRole.MODERATOR) return 1;
        return 2;
    }

    /**
     * 방 응답 DTO 생성 - 읽음/미리보기 메타데이터 포함.
     *  - unreadCount: 내가 안 읽은(내가 보낸 건 제외) 메시지 수
     *  - last message preview/sender/at: 방 목록 UI 의 미리보기
     *  - myLastReadMessageId: 내 읽음선 (방 진입 시 참조)
     *  - otherPartyLastReadMessageId: 1:1 방에서 상대 읽음선 (그룹은 null)
     */
    private ChatRoomResponse toResponse(ChatRoom room, UUID me) {
        return toResponse(room, me, null, Map.of());
    }

    private ChatRoomResponse toResponse(ChatRoom room, UUID me, UUID directOtherMemberId, Map<UUID, ChatSenderView> viewsByMemberId) {
        long participantCount = participantRepo.countByChatRoom_IdAndStatus(room.getId(), ChatParticipantStatus.JOINED);

        Long myLastRead = participantRepo.findByChatRoomIdAndMemberId(room.getId(), me)
                .map(ChatParticipant::getLastReadMessageId)
                .orElse(null);

        long unread = messageRepo.countUnread(room.getId(), me, myLastRead);

        ChatMessage last = messageRepo.findTopByChatRoom_IdOrderByIdDesc(room.getId()).orElse(null);
        Long lastMessageId = last == null ? null : last.getId();
        String lastPreview = last == null ? null : (last.isDeleted() ? "삭제된 메시지입니다." : last.getContent());
        UUID lastSenderId = last == null ? null : last.getSenderId();
        var lastAt = last == null ? null : last.getCreatedAt();

        Long otherPartyLastRead = null;
        UUID otherMemberId = null;
        ChatSenderView otherMemberView = null;
        if (room.getType() == ChatRoomType.DIRECT) {
            List<Long> others = participantRepo.findOtherPartyLastRead(room.getId(), me);
            // 1:1 상대가 아직 lastRead 가 null 이거나 참여 취소된 경우도 고려
            otherPartyLastRead = others.isEmpty() ? null : others.get(0);

            // 상대 프로필 — myRooms 에서 batch enrichment 로 전달. 단건 호출 시엔 여기서 loadSenders 를 호출.
            if (directOtherMemberId != null) {
                otherMemberId = directOtherMemberId;
                otherMemberView = viewsByMemberId.get(directOtherMemberId);
            } else {
                UUID other = participantRepo.findByChatRoomId(room.getId()).stream()
                        .filter(p -> p.getStatus() == ChatParticipantStatus.JOINED || p.getStatus() == ChatParticipantStatus.HIDDEN)
                        .map(ChatParticipant::getMemberId)
                        .filter(id -> !id.equals(me))
                        .findFirst()
                        .orElse(null);
                if (other != null) {
                    otherMemberId = other;
                    Map<UUID, ChatSenderView> m = senderEnricher.loadSenders(room.getCompanyId(), Set.of(other));
                    otherMemberView = m.get(other);
                }
            }
        }

        String displayName = resolveDisplayName(room, me);

        return ChatRoomResponse.from(
                room,
                displayName,
                participantCount,
                unread,
                lastMessageId,
                lastPreview,
                lastSenderId,
                lastAt,
                myLastRead,
                otherPartyLastRead,
                otherMemberId,
                otherMemberView
        );
    }

    @Transactional
    public void addMember(UUID actor, long roomId, UUID target) {
        ChatParticipant me = participantRepo.findByChatRoomIdAndMemberId(roomId, actor)
                .orElseThrow(() -> new ChatException(ChatErrorCode.NOT_PARTICIPANT));
        if (me.getStatus() != ChatParticipantStatus.JOINED) throw new ChatException(ChatErrorCode.NOT_PARTICIPANT);
        ChatRoom room = roomRepo.findById(roomId).orElseThrow(() -> new ChatException(ChatErrorCode.ROOM_NOT_FOUND));
        if (room.isDeleted()) throw new ChatException(ChatErrorCode.ROOM_DELETED);
        if (room.getType() != ChatRoomType.GROUP) {
            throw new ChatException(ChatErrorCode.VALIDATION_ERROR, "1:1 채팅방에는 참여자를 초대할 수 없습니다.");
        }
        persistParticipant(room, target, ChatRoomRole.MEMBER);

        // 그룹 채팅 초대 시스템 메시지
        if (room.getType() == ChatRoomType.GROUP) {
            Map<UUID, ChatSenderView> views = senderEnricher.loadSenders(room.getCompanyId(), Set.of(target));
            String targetName = views.get(target) != null ? views.get(target).name() : "구성원";
            ChatMessage system = messageRepo.save(ChatMessage.builder()
                    .chatRoom(room)
                    .senderId(actor)
                    .type(MessageType.SYSTEM)
                    .content(targetName + "님이 초대되었습니다.")
                    .build());
            pubsub.publish(roomId, ChatMessageEvent.created(system));
        }
    }

    @Transactional
    public void leave(UUID me, long roomId) {
        ChatParticipant p = participantRepo.findByChatRoomIdAndMemberId(roomId, me)
                .orElseThrow(() -> new ChatException(ChatErrorCode.NOT_PARTICIPANT));
        // 1:1 방은 HIDDEN(soft leave), 그룹은 LEFT
        if (p.getChatRoom().getType() == ChatRoomType.DIRECT) {
            p.hide();
        } else {
            p.leave();
            ChatRoom room = p.getChatRoom();
            Map<UUID, ChatSenderView> views = senderEnricher.loadSenders(room.getCompanyId(), Set.of(me));
            String memberName = views.get(me) != null ? views.get(me).name() : "구성원";
            ChatMessage system = messageRepo.save(ChatMessage.builder()
                    .chatRoom(room)
                    .senderId(me)
                    .type(MessageType.SYSTEM)
                    .content(memberName + "님이 채팅방을 나갔습니다.")
                    .build());
            pubsub.publish(roomId, ChatMessageEvent.created(system));
        }
    }

    @Transactional
    public void deleteRoom(UUID actor, long roomId, boolean adminOverride) {
        ChatRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.ROOM_NOT_FOUND));

        if (!adminOverride) {
            ChatParticipant p = participantRepo.findByChatRoomIdAndMemberId(roomId, actor)
                    .orElseThrow(() -> new ChatException(ChatErrorCode.NOT_PARTICIPANT));
            if (p.getStatus() != ChatParticipantStatus.JOINED) throw new ChatException(ChatErrorCode.NOT_PARTICIPANT);
        }
        room.softDelete();
    }

    private void persistParticipant(ChatRoom room, UUID memberId, ChatRoomRole role) {
        participantRepo.save(ChatParticipant.builder()
                .chatRoom(room)
                .memberId(memberId)
                .role(role)
                .status(ChatParticipantStatus.JOINED)
                .build());
    }

    private String resolveDisplayName(ChatRoom room, UUID me) {
        if (room.getType() == ChatRoomType.DIRECT) {
            return room.getName();
        }
        List<ChatParticipant> joined = participantRepo.findByChatRoomId(room.getId()).stream()
                .filter(p -> p.getStatus() == ChatParticipantStatus.JOINED)
                .toList();
        if (joined.isEmpty()) return room.getName();
        Set<UUID> ids = joined.stream().map(ChatParticipant::getMemberId).collect(Collectors.toSet());
        Map<UUID, ChatSenderView> views = senderEnricher.loadSenders(room.getCompanyId(), ids);
        List<String> names = joined.stream()
                .map(ChatParticipant::getMemberId)
                .map(id -> views.get(id) == null ? null : views.get(id).name())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        if (names.isEmpty()) return room.getName();
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + ", " + names.get(1);
        if (names.size() == 3) return names.get(0) + ", " + names.get(1) + ", " + names.get(2);
        return names.get(0) + ", " + names.get(1) + ", " + names.get(2) + " 외 " + (names.size() - 3) + "명";
    }
}
