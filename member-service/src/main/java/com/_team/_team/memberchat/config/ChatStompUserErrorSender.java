package com._team._team.memberchat.config;

import com._team._team.memberchat.error.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

/**
 * {@link ChannelInterceptor} 에서 던진 {@link ChatException} 은 STOMP @MessageExceptionHandler 로 가지 않아
 * 브로커가 "Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]" 만 보낸다.
 * 사용자 세션이 있으면 /user/queue/errors 로 코드·메시지를 보낸다.
 */
@Slf4j
@Component
public class ChatStompUserErrorSender {

    private final SimpMessagingTemplate messagingTemplate;

    // SimpMessagingTemplate 은 DelegatingWebSocketMessageBrokerConfiguration 에서 생성되며
    // 이 설정은 StompChatConfig → ChatChannelInterceptor → ChatStompUserErrorSender 를 거쳐
    // 다시 SimpMessagingTemplate 을 필요로 하므로 순환이 발생한다.
    // @Lazy 프록시를 주입해 사이클을 끊는다.
    public ChatStompUserErrorSender(@Lazy SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyIfPossible(Message<?> message, ChatException e) {
        String username = resolveUsername(StompHeaderAccessor.wrap(message));
        if (username == null) {
            log.warn("STOMP ChatException (no user to notify): {} - {}", e.getErrorCode().getCode(), e.getMessage());
            return;
        }
        messagingTemplate.convertAndSendToUser(username, "/queue/errors", Map.of(
                "code", e.getErrorCode().getCode(),
                "message", e.getMessage()
        ));
    }

    private String resolveUsername(StompHeaderAccessor acc) {
        Principal user = acc.getUser();
        if (user != null) {
            return user.getName();
        }
        if (acc.getSessionAttributes() != null) {
            Object p = acc.getSessionAttributes().get("principal");
            if (p instanceof ChatStompHandler.AuthPrincipal ap) {
                return ap.userId().toString();
            }
        }
        return null;
    }
}
