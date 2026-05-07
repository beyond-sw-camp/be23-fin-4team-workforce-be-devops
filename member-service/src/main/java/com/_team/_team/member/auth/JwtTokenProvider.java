package com._team._team.member.auth;

import com._team._team.dto.BusinessException;
import com._team._team.member.domain.Member;
import com._team._team.member.domain.MemberPosition;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtTokenProvider {
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.expirationAt}")
    private int expirationAt;

    @Value("${jwt.secretKeyAt}")
    private String secretKeyAt;

    @Value("${jwt.expirationRt}")
    private int expirationRt;

    @Value("${jwt.secretKeyRt}")
    private String secretKeyRt;

    private Key atKey;
    private Key rtKey;

    @Autowired
    public JwtTokenProvider(
            @Qualifier("rtInventory") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        byte[] atByte = Base64.getDecoder().decode(secretKeyAt);
        this.atKey = Keys.hmacShaKeyFor(atByte);
        byte[] rtByte = Base64.getDecoder().decode(secretKeyRt);
        this.rtKey = Keys.hmacShaKeyFor(rtByte);
    }

    // AT 생성 - 일반 멤버 (position 필수) / SaaS 운영자 (position null 허용)
    public String createAtToken(Member member, MemberPosition memberPosition) {
        Claims claims = Jwts.claims().setSubject(member.getMemberId().toString());
        claims.put("companyId", member.getCompany().getCompanyId().toString());
        claims.put("name", member.getName());

        if (memberPosition == null) {
            // SaaS 운영자 - position 정보 없음
            claims.put("actor_type", "OPERATOR");
        } else {
            claims.put("actor_type", "MEMBER");
            claims.put("memberPositionId", memberPosition.getMemberPositionId().toString());
            claims.put("organizationId", memberPosition.getOrganization().getOrganizationId().toString());
            claims.put("isSystemAdmin", memberPosition.getIsSystemAdminYn());
        }

        Date now = new Date();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationAt * 60 * 1000L))
                .signWith(atKey)
                .compact();
    }

    // RT 생성 + Redis 저장
    public String createRtToken(Member member) {
        Claims claims = Jwts.claims().setSubject(member.getMemberId().toString());
        claims.put("email", member.getEmail());

        Date now = new Date();

        String refreshToken = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationRt * 60 * 1000L))
                .signWith(rtKey)
                .compact();

        redisTemplate.opsForValue().set(
                "RT:" + member.getMemberId(),
                refreshToken,
                expirationRt,
                TimeUnit.MINUTES
        );

        return refreshToken;
    }

    // RT → HttpOnly 쿠키로 전달
    public void setRtCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);   // 로컬 개발 시 false, 운영 시 true
        cookie.setPath("/");
        cookie.setMaxAge(expirationRt * 60);
        response.addCookie(cookie);
    }

    // 로그아웃 시 쿠키 삭제
    public void deleteRtCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    // 쿠키에서 RT 꺼내기
    public String getRtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals("refreshToken"))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    // AT 검증
    public Claims validateAtToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(atKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    // RT 검증
    public Claims validateRtToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(rtKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return e.getClaims();
        }
    }
    // RT 쿠키 검증 후 Claims 반환
    public Claims validateAndGetRtClaims(
            HttpServletRequest request, String memberId) {

        // 1. 쿠키에서 RT 꺼내기
        String refreshToken = getRtFromCookie(request);

        if (refreshToken == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "Refresh Token이 없습니다."
            );
        }

        // 2. RT 서명 검증
        Claims claims = validateRtToken(refreshToken);

        // 3. RT 만료 확인
        if (claims.getExpiration().before(new Date())) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "Refresh Token이 만료됐습니다."
            );
        }

        // 4. Redis에서 RT 유효성 확인
        if (!isRtValid(memberId, refreshToken)) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."
            );
        }

        return claims;
    }
    // Redis에서 RT 유효성 확인
    public boolean isRtValid(String memberId, String refreshToken) {
        String redisRt = redisTemplate.opsForValue().get("RT:" + memberId);
        return redisRt != null && redisRt.equals(refreshToken);
    }

    // Redis에서 RT 삭제 (로그아웃)
    public void deleteRtToken(String memberId) {
        redisTemplate.delete("RT:" + memberId);
    }
}
