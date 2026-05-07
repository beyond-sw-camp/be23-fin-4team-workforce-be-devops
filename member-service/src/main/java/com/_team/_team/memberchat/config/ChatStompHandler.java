package com._team._team.memberchat.config;

import com._team._team.memberchat.error.ChatErrorCode;
import com._team._team.memberchat.error.ChatException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.Principal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * WebSocket CONNECT 시 JWT 를 검증해서 Authentication 을 세션에 고정한다.
 * SUBSCRIBE/SEND 에서는 Principal 만을 신뢰하고 payload 의 sender 는 절대 신뢰하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStompHandler implements ChannelInterceptor {

    private final ChatStompUserErrorSender stompUserErrorSender;

    // 기존 member-service 의 AccessToken 비밀키와 동일한 키로 검증
    @Value("${jwt.secretKeyAt}")
    private String secretKey;

    private Key key;

    @PostConstruct
    public void init() {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
        StompCommand cmd = acc.getCommand();
        if (cmd == null) return message;

        try {
            switch (cmd) {
                case CONNECT -> onConnect(acc);
                case SUBSCRIBE, SEND, UNSUBSCRIBE, DISCONNECT -> requirePrincipalOrRehydrate(acc);
                default -> { /* no-op */ }
            }
        } catch (ChatException e) {
            // CONNECT 실패는 STOMP ERROR 로 전달되도록 그대로 전파
            if (cmd == StompCommand.CONNECT) {
                throw e;
            }
            stompUserErrorSender.notifyIfPossible(message, e);
            return null;
        }
        return message;
    }

    private void onConnect(StompHeaderAccessor acc) {
        String bearer = acc.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            throw new ChatException(ChatErrorCode.INVALID_JWT, "Missing bearer token");
        }
        Claims claims;
        try {
            claims = Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(bearer.substring(7))
                    .getBody();
        } catch (Exception e) {
            log.warn("STOMP JWT parse failed: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.INVALID_JWT, e.getMessage());
        }

        // 기존 JwtTokenProvider 의 AT claim 구조에 맞춘다: subject=memberId
        UUID userId = UUID.fromString(claims.getSubject());
        UUID companyId = UUID.fromString(String.valueOf(claims.get("companyId")));
        Boolean isSystemAdmin = Boolean.TRUE.equals(claims.get("isSystemAdmin"));
        // JWT 에 role 클레임이 없으므로 isSystemAdmin → HR_ADMIN 으로 매핑.
        // 세부 role(MANAGER, AUDITOR)은 추후 RoleService lookup 에 위임 가능.
        String role = isSystemAdmin ? "HR_ADMIN" : "EMPLOYEE";

        AuthPrincipal principal = new AuthPrincipal(userId, companyId, role);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        acc.setUser(auth);
        Map<String, Object> attrs = Objects.requireNonNull(acc.getSessionAttributes());
        attrs.put("principal", principal);
        log.debug("STOMP CONNECT authenticated userId={} sessionId={}", userId, acc.getSessionId());
    }

    private void requirePrincipalOrRehydrate(StompHeaderAccessor acc) {
        Principal p = acc.getUser();
        if (p instanceof Authentication auth && auth.getPrincipal() instanceof AuthPrincipal) {
            return; // 이미 CONNECT 시점에 세팅됨
        }

        // user 가 비어 있으면 세션 속성에서 복구 시도
        Map<String, Object> attrs = acc.getSessionAttributes();
        if (attrs != null) {
            Object stored = attrs.get("principal");
            if (stored instanceof AuthPrincipal ap) {
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        ap, null, List.of(new SimpleGrantedAuthority("ROLE_" + ap.role())));
                acc.setUser(auth);
                return;
            }
        }
        throw new ChatException(ChatErrorCode.INVALID_JWT, "인증되지 않은 STOMP 프레임입니다.");
    }

    /**
     * 세션 내에서 신뢰 가능한 사용자 정보.
     * payload 의 sender 필드가 아닌 이 principal 만 신뢰한다.
     */
    public record AuthPrincipal(UUID userId, UUID companyId, String role) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }

        public boolean isHrAdminOrAuditor() {
            return "HR_ADMIN".equals(role) || "AUDITOR".equals(role);
        }
    }
}
