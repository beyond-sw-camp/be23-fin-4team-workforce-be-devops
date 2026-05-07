package com._team._team.memberchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis fixed-window counter 기반의 간단한 rate limiter.
 * 프로덕션에서는 Redis Lua(토큰버킷) 로 전환 권장.
 *
 * Redis 미가용 시에는 채팅 전송을 막지 않고 통과시키는 fail-open 정책.
 */
@Slf4j
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;

    public RateLimiter(@Qualifier("memberChatRedisTemplate") StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }
            return count == null || count <= limit;
        } catch (RuntimeException e) {
            // Redis 연결 실패 등으로 rate-limit 조회 불가 → 전송을 막지 않는다.
            log.warn("RateLimiter fail-open: key={} err={}", key, e.toString());
            return true;
        }
    }
}
