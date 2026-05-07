package com._team._team.memberchat.service;

import com._team._team.memberchat.MemberChatProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * chat-ref 의 RedisPubSubService(단일 "chat" 채널) 와 달리 방 단위 채널을 사용하여 fan-out 효율화.
 * 기존 챗봇과 채널이 겹치지 않도록 prefix "member-chat:room:" 사용.
 */
@Slf4j
@Component
public class RedisChatPubSubService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MemberChatProperties props;

    public RedisChatPubSubService(@Qualifier("memberChatRedisTemplate") StringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  MemberChatProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public String channelOf(long roomId) {
        return props.getRedis().getChannelPrefix() + roomId;
    }

    public void publish(long roomId, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redis.convertAndSend(channelOf(roomId), json);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize chat event", e);
            throw new RuntimeException(e);
        }
    }
}
