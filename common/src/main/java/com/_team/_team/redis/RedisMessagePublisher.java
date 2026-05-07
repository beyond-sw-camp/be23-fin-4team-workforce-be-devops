package com._team._team.redis;

import com._team._team.dto.NotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisMessagePublisher implements MessagePublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisMessagePublisher(
            @Qualifier("pubSubRedisTemplate")
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
        log.info("Redis Pub/Sub 발행 성공 channel: {}", channel);
    }

    // NotificationMessage 객체 발행용
    public void publish(String channel, NotificationMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            publish(channel, payload);
        } catch (Exception e) {
            log.error("Redis Pub/Sub 발행 실패: {}", e.getMessage());
        }
    }
}