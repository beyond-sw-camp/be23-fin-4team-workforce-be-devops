package com._team._team.notification.subscriber;

import com._team._team.dto.NotificationMessage;
import com._team._team.notification.sse.SseEmitterManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final SseEmitterManager sseEmitterManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            NotificationMessage notification =
                    objectMapper.readValue(body, NotificationMessage.class);

            sseEmitterManager.send(
                    notification.getReceiverId(),
                    notification,
                    notification.getEventId() // ← ID 포함
            );
        } catch (Exception e) {
            log.error("Redis 메시지 수신 실패: {}", e.getMessage());
        }
    }
}