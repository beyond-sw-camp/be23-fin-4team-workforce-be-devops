package com._team._team;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Base64;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter {

    @Value("${jwt.secretKey}")
    private String secretKey;

    private Key key;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @PostConstruct
    public void init() {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    // 토큰 없이 접근 가능한 경로
    private static final List<String> ALLOWED_PATH = List.of(
            "/member/login",
            "/member/change-password",
            "/member/generate-at",
            "/member/login",
            "/company/check-business-number",
            "/company/send-verification-code",
            "/company/verify-code",
            "/company/onboarding",
            "/member/reset-password/send-code",
            "/member/reset-password/verify-code",
            "/member/reset-password",
            "/mc/**", // 사내 채팅 WS/STOMP - 인증은 STOMP CONNECT 프레임에서 처리
            "/attendance/leave-types/internal",
            "/leave-policies/internal",
            "/work-schedules/internal",
            "/attendance/overtime-policies/internal",
            "/company-holidays/internal",
            "/salary/salary-item-templates/internal",
            "/salary/salary-policies/internal",
            "/salary/pay-grade-table/internal",
            "/approval/documents/internal",
            "/approval/documents/internal/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String urlPath = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // 1. OPTIONS 요청(Preflight)은 무조건 통과 (Gateway globalcors에서 처리)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }

        // 2. 허용 경로 체크
        boolean isAllowed = ALLOWED_PATH.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, urlPath));
        
        if (isAllowed) {
            return chain.filter(exchange);
        }

        // 3. 토큰 검증 로직
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        try {
            if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
                throw new IllegalArgumentException("token이 없거나, 형식이 잘못되었습니다.");
            }

            String token = bearerToken.substring(7);

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // actor_type 분기 - SaaS 운영자 , SaaS 이용자
            String actorType = claims.get("actor_type", String.class);
            boolean isOperator = "OPERATOR".equals(actorType);
            boolean isSaasPath = urlPath.startsWith("/saas/");

            // 경로 권한 검증
            if (isSaasPath && !isOperator) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            if (!isSaasPath && isOperator) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // 헤더 전달 - 운영자/이용자 분기
            ServerWebExchange serverWebExchange;
            if (isOperator) {
                String operatorEmail = claims.getSubject();
                String operatorName = claims.get("name", String.class);
                serverWebExchange = exchange.mutate()
                        .request(r -> r
                                .header("X-Actor-Type", "OPERATOR")
                                .header("X-Operator-Email", operatorEmail != null ? operatorEmail : "")
                                .header("X-Operator-Name", operatorName != null ? operatorName : ""))
                        .build();
            } else {
                String memberId = claims.getSubject();
                String memberPositionId = claims.get("memberPositionId", String.class);
                String companyId = claims.get("companyId", String.class);
                String name = claims.get("name", String.class);
                String isSystemAdmin = claims.get("isSystemAdmin", String.class);
                serverWebExchange = exchange.mutate()
                        .request(r -> r
                                .header("X-Actor-Type", "MEMBER")
                                .header("X-User-UUID", memberId != null ? memberId : "")
                                .header("X-User-MemberPositionId", memberPositionId != null ? memberPositionId : "")
                                .header("X-User-CompanyId", companyId != null ? companyId : "")
                                .header("X-User-Name", name != null ? name : "")
                                .header("X-User-IsSystemAdmin", isSystemAdmin != null ? isSystemAdmin : ""))
                        .build();
            }

            return chain.filter(serverWebExchange);

        } catch (Exception e) {
            e.printStackTrace();
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    // 에러 응답 유틸리티
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        return exchange.getResponse().setComplete();
    }
}
