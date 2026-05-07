package com._team._team.memberchat.service;

import com._team._team.memberchat.domain.ChatMessage;
import com._team._team.memberchat.domain.ChatParticipant;
import com._team._team.memberchat.domain.ChatReadReceipt;
import com._team._team.memberchat.repository.ChatMessageRepository;
import com._team._team.memberchat.repository.ChatReadReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 멀티디바이스 일관성을 위해 lastReadMessageId 는 단조증가(monotonic)로만 갱신.
 * 클라이언트는 디바이스마다 다른 deviceId 를 보낸다.
 *
 * 읽음 처리 시나리오:
 *  - 방 진입 시 클라이언트가 {@link #ackLatest} 를 호출 → 서버가 방의 최신 messageId 까지 일괄 ack
 *  - 새 메시지가 실시간으로 들어와 사용자가 해당 메시지를 "보게" 되면 {@link #ack} 로 개별 ack
 *  - 둘 다 동일하게 READ 이벤트를 Redis 로 발행해 다른 참여자 UI 에 반영
 *
 * 카톡 스타일 UX:
 *  - 1:1: 상대의 lastReadMessageId 가 내 메시지 id 이상이면 내 메시지의 "1"(안읽음) 이 사라짐
 *  - 그룹: 각 메시지 옆 "안 읽은 수" = (참여자 수 - 읽은 수); 0 이면 안내 제거
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadReceiptService {

    private final ChatReadReceiptRepository receiptRepo;
    private final ChatMessageRepository messageRepo;
    private final ChatAuthPolicy authPolicy;
    private final RedisChatPubSubService pubsub;

    /**
     * 방 진입 시 호출. 방의 최신 메시지까지 일괄 ack.
     * 최신 메시지가 없거나 이미 그 id 를 읽은 상태라면 no-op (ack 이벤트도 내려보내지 않음).
     *
     * @return 새로 기록된 lastReadMessageId (변화 없으면 기존 값)
     */
    @Transactional
    public Long ackLatest(UUID memberId, long roomId, String deviceId) {
        ChatParticipant p = authPolicy.requireActiveParticipant(memberId, roomId);
        Long latest = messageRepo.findTopByChatRoom_IdOrderByIdDesc(roomId)
                .map(ChatMessage::getId)
                .orElse(null);
        if (latest == null) return p.getLastReadMessageId();

        Long prev = p.getLastReadMessageId();
        if (prev != null && prev >= latest) {
            return prev; // 이미 최신까지 읽음
        }
        return doAck(p, roomId, memberId, latest, deviceId);
    }

    /**
     * 특정 메시지까지 읽었음을 기록. 단조 증가이므로 과거 messageId 는 무시한다(idempotent).
     */
    @Transactional
    public void ack(UUID memberId, long roomId, long messageId, String deviceId) {
        ChatParticipant p = authPolicy.requireActiveParticipant(memberId, roomId);
        Long prev = p.getLastReadMessageId();
        if (prev != null && prev >= messageId) {
            // 이미 같은/더 큰 id 를 읽은 상태 → 이벤트 발행 생략 (idempotent)
            return;
        }
        doAck(p, roomId, memberId, messageId, deviceId);
    }

    private Long doAck(ChatParticipant p, long roomId, UUID memberId, long messageId, String deviceId) {
        p.updateLastRead(messageId);

        // 개별 receipt 는 감사/통계용 — 없으면 생성 (중복 저장 방지)
        receiptRepo.findByMessageIdAndMemberId(messageId, memberId)
                .orElseGet(() -> receiptRepo.save(ChatReadReceipt.builder()
                        .roomId(roomId)
                        .messageId(messageId)
                        .memberId(memberId)
                        .deviceId(deviceId)
                        .build()));

        // 방의 모든 참여자에게 브로드캐스트. lastReadMessageId 를 함께 내려보내 프론트가 상대의 읽음선을 갱신 가능.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "READ");
        payload.put("roomId", roomId);
        payload.put("memberId", memberId.toString());
        payload.put("messageId", messageId);
        payload.put("lastReadMessageId", messageId);
        pubsub.publish(roomId, payload);

        return messageId;
    }
}
