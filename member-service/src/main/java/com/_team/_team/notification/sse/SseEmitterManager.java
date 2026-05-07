package com._team._team.notification.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterManager {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter add(UUID memberId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);

        emitter.onCompletion(() -> {
            emitters.remove(memberId);
            log.info("SSE 연결 종료 memberId: {}", memberId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(memberId);
            log.info("SSE 연결 타임아웃 memberId: {}", memberId);
        });
        emitter.onError(e -> {
            emitters.remove(memberId);
            log.error("SSE 연결 에러 memberId: {}", memberId);
        });

        emitters.put(memberId, emitter);

        // 연결 직후 더미 이벤트
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected")
                    .reconnectTime(3000)); // ← 3초 후 재연결 힌트
        } catch (Exception e) {
            emitters.remove(memberId);
        }

        return emitter;
    }

    // Heartbeat 30초마다
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        emitters.forEach((memberId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("ping"));
            } catch (Exception e) {
                emitters.remove(memberId);
                log.info("Heartbeat 실패 → 연결 제거 memberId: {}", memberId);
            }
        });
    }

    public void send(UUID memberId, Object data, String eventId) {
        SseEmitter emitter = emitters.get(memberId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .id(eventId)          // ← ID 설정
                        .name("notification")
                        .data(data));
            } catch (Exception e) {
                emitters.remove(memberId);
                log.error("SSE 전송 실패 memberId: {}", memberId);
            }
        }
    }
}