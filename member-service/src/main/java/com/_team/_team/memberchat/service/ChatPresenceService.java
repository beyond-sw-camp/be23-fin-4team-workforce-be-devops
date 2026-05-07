package com._team._team.memberchat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 영속화하지 않는 가벼운 실시간 시그널 — 현재는 타이핑 인디케이터.
 *
 *  - 권한: {@link ChatAuthPolicy#requireActiveParticipant} 로 방 참여자만 발송 가능.
 *  - 레이트리밋: 사용자/방 단위 2초 1회 (RateLimiter fail-open).
 *  - 영속화 X: Redis pub-sub 만 사용 — 구독자가 받지 못하면 자연스럽게 만료된다.
 *
 * 클라이언트는 본인이 발송한 typing 이벤트를 토픽 fan-out 으로 다시 받게 되므로
 * `senderId === me` 로 직접 필터링한다. (서버에서 user destination 분기를 두지 않음)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPresenceService {

    private static final Duration TYPING_RATE_WINDOW = Duration.ofSeconds(2);
    private static final int TYPING_RATE_LIMIT = 1;

    private final ChatAuthPolicy authPolicy;
    private final RedisChatPubSubService pubsub;
    private final RateLimiter rateLimiter;

    /**
     * 타이핑 시그널 처리.
     *  - 권한 검증 후 Redis 로 publish → 구독자(RedisChatSubscriberConfig)가
     *    `/mc/topic/typing/{roomId}` 로 fan-out.
     */
    public void handleTyping(UUID userId, long roomId) {
        // 1) 권한 — 비참여자/탈퇴는 NOT_PARTICIPANT 로 거절
        authPolicy.requireActiveParticipant(userId, roomId);

        // 2) 레이트리밋 (사용자·방 단위)
        String key = "member-chat:typing:" + userId + ":" + roomId;
        if (!rateLimiter.tryAcquire(key, TYPING_RATE_LIMIT, TYPING_RATE_WINDOW)) {
            // 너무 자주 보내면 조용히 무시 — 클라가 throttle 해야 정상이지만 추가 보호.
            return;
        }

        // 3) 이벤트 publish (RedisChatSubscriberConfig 가 eventType=TYPING 을 보고 typing 토픽으로 라우팅)
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "TYPING");
        payload.put("roomId", roomId);
        payload.put("memberId", userId.toString());
        payload.put("at", Instant.now().toString());
        pubsub.publish(roomId, payload);
    }
}
