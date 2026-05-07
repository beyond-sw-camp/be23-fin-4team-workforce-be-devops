package com._team._team.memberchat.config;

import com._team._team.memberchat.error.ChatErrorCode;
import com._team._team.memberchat.error.ChatException;
import com._team._team.memberchat.service.ChatAuthPolicy;
import com._team._team.memberchat.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
//import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.security.Principal;
import java.time.Duration;
import java.util.Objects;

/**
 * ChatStompHandler 다음에 실행되어 rate-limit / MDC / destination 검증을 수행한다.
 * Principal 은 이미 세팅돼 있다고 가정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatChannelInterceptor implements ChannelInterceptor {

    private final ChatStompUserErrorSender stompUserErrorSender;

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final String SEND_PATTERN_SEND = "/mc/app/room/{roomId}/send";
    private static final String SEND_PATTERN_READ = "/mc/app/room/{roomId}/read";
    private static final String SUB_PATTERN_ROOM  = "/mc/topic/room/{roomId}";
    private static final String SUB_PATTERN_READ  = "/mc/topic/read/{roomId}";
    private static final String SUB_PATTERN_PRES  = "/mc/topic/presence/{roomId}";

    private final ChatAuthPolicy authPolicy;
    private final RateLimiter rateLimiter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
        StompCommand cmd = acc.getCommand();
        if (cmd == null) return message;

        ChatStompHandler.AuthPrincipal principal = resolvePrincipal(acc);

        // STOMP SEND 가 서버에 도달했는지 즉시 확인할 수 있도록 원시 로그
        if (cmd == StompCommand.SEND || cmd == StompCommand.SUBSCRIBE) {
            log.info("[STOMP IN] cmd={} dest={} sessionId={} principal={}",
                    cmd, acc.getDestination(), acc.getSessionId(),
                    principal == null ? "null" : principal.userId());
        }

        try {
            if (principal != null) {
                MDC.put("userId", principal.userId().toString());
                MDC.put("sessionId", Objects.toString(acc.getSessionId(), ""));
            }

            try {
                switch (cmd) {
                    case SUBSCRIBE -> handleSubscribe(acc, principal);
                    case SEND -> handleSend(acc, principal);
                    default -> { /* no-op */ }
                }
            } catch (ChatException e) {
                log.warn("[STOMP DROP] cmd={} dest={} code={} msg={}",
                        cmd, acc.getDestination(), e.getErrorCode(), e.getMessage());
                stompUserErrorSender.notifyIfPossible(message, e);
                return null;
            } catch (RuntimeException e) {
                // Redis 장애 등 예상치 못한 런타임 예외가 조용히 프레임을 드롭하지 않도록
                log.error("[STOMP ERROR] cmd={} dest={} err={}",
                        cmd, acc.getDestination(), e.toString(), e);
                return null;
            }
        } finally {
            // MDC 는 outbound 에서 정리하므로 여기서 바로 remove 하지 않는다.
        }
        return message;
    }

    private void handleSubscribe(StompHeaderAccessor acc, ChatStompHandler.AuthPrincipal p) {
        String dest = acc.getDestination();
        if (dest == null) return;

        Long roomId = matchRoomId(dest, SUB_PATTERN_ROOM, SUB_PATTERN_READ, SUB_PATTERN_PRES);
        if (roomId == null) return; // 기타 destination (/user/queue/errors 등)
        authPolicy.canSubscribe(p, roomId);
    }

    private void handleSend(StompHeaderAccessor acc, ChatStompHandler.AuthPrincipal p) {
        String dest = acc.getDestination();
        if (dest == null) return;

        Long roomId = matchRoomId(dest, SEND_PATTERN_SEND, SEND_PATTERN_READ);
        if (roomId == null) return;

        if (p == null) {
            throw new ChatException(ChatErrorCode.INVALID_JWT, "SEND 에 대한 인증 정보가 없습니다.");
        }

        // rate-limit: per user+room, 60 / minute
        boolean ok = rateLimiter.tryAcquire(
                "mc:rate:" + p.userId() + ":" + roomId, 60, Duration.ofMinutes(1));
        if (!ok) {
            throw new ChatException(ChatErrorCode.RATE_LIMIT);
        }
        authPolicy.canSubscribe(p, roomId); // 전송 전에도 참가 여부 재확인
    }

    private Long matchRoomId(String destination, String... patterns) {
        for (String pattern : patterns) {
            if (MATCHER.match(pattern, destination)) {
                var vars = MATCHER.extractUriTemplateVariables(pattern, destination);
                String raw = vars.get("roomId");
                if (raw == null) continue;
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException e) {
                    throw new ChatException(ChatErrorCode.VALIDATION_ERROR, "invalid roomId");
                }
            }
        }
        return null;
    }

    /**
     * Principal 을 세션/Authentication 어디에 담겨 있든 안전하게 꺼낸다.
     */
    private ChatStompHandler.AuthPrincipal resolvePrincipal(StompHeaderAccessor acc) {
        Principal user = acc.getUser();
        if (user instanceof ChatStompHandler.AuthPrincipal ap) {
            return ap;
        }
//        if (user instanceof Authentication auth
//                && auth.getPrincipal() instanceof ChatStompHandler.AuthPrincipal ap) {
//            return ap;
//        }
        if (acc.getSessionAttributes() != null) {
            Object stored = acc.getSessionAttributes().get("principal");
            if (stored instanceof ChatStompHandler.AuthPrincipal ap) {
                return ap;
            }
        }
        return null;
    }
}
