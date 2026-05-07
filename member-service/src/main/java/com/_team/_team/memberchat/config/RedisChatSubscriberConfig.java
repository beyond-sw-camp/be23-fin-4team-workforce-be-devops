package com._team._team.memberchat.config;

import com._team._team.memberchat.MemberChatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

/**
 * member-chat:room:{roomId} 채널을 pattern subscribe 하여
 * 로컬 인스턴스에 연결된 STOMP 구독자에게 /mc/topic/room/{roomId} 로 fan-out 한다.
 *
 * 기존 챗봇/chat-ref 의 "chat" 채널과 구독 패턴이 겹치지 않도록 "member-chat:room:*" 로만 구독한다.
 *
 * ⚠️ Spring 의 {@link org.springframework.data.redis.listener.adapter.MessageListenerAdapter} 는
 * pattern subscribe 시 두 번째 String 인자로 실제 channel 이 아닌 "pattern" 을 넘겨주는 경우가 있어
 * roomId 파싱이 실패하는 문제가 있었음. 따라서 {@link MessageListener} 를 직접 구현해
 * Redis {@link Message} 원본을 받고 channel/pattern 을 각각 꺼내 쓰도록 한다.
 */
@Slf4j
@Configuration
public class RedisChatSubscriberConfig {

    private final RedisConnectionFactory connectionFactory;
    private final SimpMessagingTemplate messagingTemplate;
    private final MemberChatProperties props;
    private final ObjectMapper objectMapper;

    public RedisChatSubscriberConfig(
            @Qualifier("memberChatRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            SimpMessagingTemplate messagingTemplate,
            MemberChatProperties props,
            ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.messagingTemplate = messagingTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Bean
    public RedisMessageListenerContainer memberChatListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MemberChatRedisListener listener = new MemberChatRedisListener(
                messagingTemplate,
                objectMapper,
                props.getStomp().getTopicPrefix(),
                props.getRedis().getChannelPrefix());

        // pattern: member-chat:room:*
        String pattern = props.getRedis().getChannelPrefix() + "*";
        container.addMessageListener(listener, new PatternTopic(pattern));
        log.info("[mc] redis subscribe pattern={}", pattern);
        return container;
    }

    /**
     * Redis 로 들어오는 이벤트를 STOMP 목적지로 전달.
     *  - 기본: /mc/topic/room/{roomId}
     *  - READ 이벤트(eventType=READ) 는 /mc/topic/read/{roomId} 로 별도 전달
     *  - TYPING 이벤트(eventType=TYPING) 는 /mc/topic/typing/{roomId} 로 별도 전달
     */
    @Slf4j
    public static class MemberChatRedisListener implements MessageListener {

        private final SimpMessagingTemplate messagingTemplate;
        private final ObjectMapper objectMapper;
        private final String topicPrefix;
        private final String channelPrefix;

        public MemberChatRedisListener(SimpMessagingTemplate messagingTemplate,
                                       ObjectMapper objectMapper,
                                       String topicPrefix,
                                       String channelPrefix) {
            this.messagingTemplate = messagingTemplate;
            this.objectMapper = objectMapper;
            this.topicPrefix = topicPrefix;
            this.channelPrefix = channelPrefix;
        }

        @Override
        public void onMessage(Message message, byte[] patternBytes) {
            String channel = message.getChannel() == null
                    ? null
                    : new String(message.getChannel(), StandardCharsets.UTF_8);
            String json = message.getBody() == null
                    ? null
                    : new String(message.getBody(), StandardCharsets.UTF_8);

            if (json == null || json.isBlank()) {
                log.warn("[mc-sub] empty body channel={}", channel);
                return;
            }

            try {
                JsonNode node = objectMapper.readTree(json);

                // 1) 우선 channel 에서 roomId 추출 (member-chat:room:{id})
                Long roomId = extractRoomIdFromChannel(channel);

                // 2) 실패 시 payload 의 roomId 필드 fallback
                if (roomId == null && node.hasNonNull("roomId")) {
                    try { roomId = node.get("roomId").asLong(); }
                    catch (Exception ignore) { /* noop */ }
                }

                if (roomId == null) {
                    log.warn("[mc-sub] cannot resolve roomId channel={} payload={}", channel, json);
                    return;
                }

                String eventType = node.hasNonNull("eventType") ? node.get("eventType").asText() : "";
                String type      = node.hasNonNull("type")      ? node.get("type").asText()      : "MESSAGE";
                boolean isRead = "READ".equalsIgnoreCase(eventType) || "READ".equalsIgnoreCase(type);
                boolean isTyping = "TYPING".equalsIgnoreCase(eventType) || "TYPING".equalsIgnoreCase(type);

                String destination;
                if (isTyping) {
                    destination = topicPrefix + "/typing/" + roomId;
                } else if (isRead) {
                    destination = topicPrefix + "/read/" + roomId;
                } else {
                    destination = topicPrefix + "/room/" + roomId;
                }

                messagingTemplate.convertAndSend(destination, json);
                log.debug("[mc-sub] forward channel={} dest={} bytes={}", channel, destination, json.length());
            } catch (Exception e) {
                log.error("[mc-sub] failed to forward redis event channel={} payload={}", channel, json, e);
            }
        }

        private Long extractRoomIdFromChannel(String channel) {
            if (channel == null || channelPrefix == null) return null;
            if (!channel.startsWith(channelPrefix)) return null;
            String tail = channel.substring(channelPrefix.length());
            if (tail.isEmpty() || "*".equals(tail)) return null;
            try {
                return Long.parseLong(tail);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
