package com._team._team.memberchat.config;

import com._team._team.memberchat.MemberChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 기존 chat-ref 의 StompWebSocketConfig 와 경로/프리픽스가 겹치지 않도록
 *  - endpoint: /mc/connect
 *  - app prefix: /mc/app
 *  - topic prefix: /mc/topic
 * 으로 분리한다. (챗봇은 REST 기반이라 Stomp 설정 자체가 없었지만,
 *  chat-ref 레거시 코드와도 안전하게 공존 가능)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class StompChatConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatStompHandler chatStompHandler;
    private final ChatChannelInterceptor chatChannelInterceptor;
    private final MemberChatProperties props;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Origin 검증은 유지하되, SockJS 응답에 CORS 헤더를 붙이지 않는다.
        // API Gateway(globalcors)가 이미 Allow-Origin 을 붙이므로, 여기서까지 붙이면
        // 브라우저가 "Access-Control-Allow-Origin 에 값이 두 개"로 차단한다.
        registry.addEndpoint(props.getWebsocket().getEndpoint())
                .setAllowedOriginPatterns(props.getWebsocket().getAllowedOrigins().toArray(String[]::new))
                .withSockJS()
                .setSuppressCors(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(props.getStomp().getAppPrefix());
        registry.enableSimpleBroker(props.getStomp().getTopicPrefix(), "/user/queue");
        registry.setUserDestinationPrefix(props.getStomp().getUserPrefix());
    }

    /**
     * inbound 채널에 interceptor 를 "순서대로" 붙인다.
     * 1) ChatStompHandler : CONNECT 시 JWT -> Principal 세팅
     * 2) ChatChannelInterceptor : SUBSCRIBE/SEND 검증 (Principal 필요)
     * 순서가 바뀌면 SEND 때 Principal 이 없어 NOT_PARTICIPANT/INVALID_JWT 로 조용히 죽는다.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatStompHandler, chatChannelInterceptor);
    }
}
