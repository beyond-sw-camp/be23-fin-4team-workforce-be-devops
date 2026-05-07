package com._team._team.goal.permission;

import com._team._team.annotation.Action;
import com._team._team.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * goal-service 전용 권한 보조 판별기.
 *
 * <p>{@code @CheckPermission} 어노테이션이 표현하지 못하는 케이스를 메서드 본문에서 체크하기 위한 헬퍼이다.
 * Redis 키({@code PERMISSION:{memberPositionId}})는 {@link com._team._team.annotation.PermissionAspect} 와 동일하게 사용한다.
 *
 * <p>이게 왜 필요한가 — goal-service 는 같은 {@code goal} 테이블에 조직(Objective) 과 개인(KR) 데이터가 섞여 있어,
 * 동일한 엔드포인트라도 요청 내용에 따라 권한 정책이 달라진다. 어노테이션으로는 표현이 어려운 4가지 패턴을 지원한다.
 *
 * <ol>
 *   <li><b>조건부 권한 검사</b> — 예: {@code POST /goal} 에서 {@code ownerType == ORGANIZATION} 일 때만 별도 권한 요구.
 *       어노테이션은 메서드 진입 전 무조건 검사라 바디 값에 따른 분기가 안 된다.</li>
 *   <li><b>권한 결과를 boolean 으로 서비스에 전달</b> — 예: EVALUATION READ 권한이 있으면 응답에 평가 점수까지 포함.
 *       어노테이션은 통과/거부만 한다.</li>
 *   <li><b>Scope-aware 검사</b> — 예: 조직 목표는 TEAM/COMPANY 범위만 인정, SELF 는 거부.
 *       어노테이션은 {@code RESOURCE:ACTION} prefix 만 보므로 SELF 권한도 통과시킨다.</li>
 *   <li><b>여러 권한의 OR 조합</b> — 예: GOAL CREATE 또는 UPDATE 중 하나만 있어도 허용.
 *       어노테이션 한 개로는 표현 불가.</li>
 * </ol>
 *
 * <p>모든 메서드는 권한 미보유/조회 실패 시 {@code false} 만 반환하고 예외를 던지지 않는다.
 * 호출부에서 직접 분기하거나 {@link com._team._team.dto.BusinessException} 으로 변환할지 결정한다.
 *
 * <p>현재는 goal-service 에만 두지만, 다른 서비스에서도 같은 요구가 생기면 common 으로 옮겨 재사용할 것.
 */
@Component
public class PositionPermissionReader {

    private final RedisTemplate<String, String> permissionRedisTemplate;

    public PositionPermissionReader(
            @Qualifier("permissionInventory") RedisTemplate<String, String> permissionRedisTemplate) {
        this.permissionRedisTemplate = permissionRedisTemplate;
    }

    public boolean isSystemAdmin(HttpServletRequest request) {
        return "YES".equals(request.getHeader("X-User-IsSystemAdmin"));
    }

    /** 시스템 관리자이거나 Redis 에 해당 권한이 있으면 true. 미보유·미조회는 false (예외 없음). */
    public boolean hasPermission(HttpServletRequest request, Resource resource, Action action) {
        if (isSystemAdmin(request)) {
            return true;
        }
        String required = resource.name() + ":" + action.name();
        return getGrantedPermissions(request).stream().anyMatch(p -> p.startsWith(required));
    }

    public boolean hasPermissionInAnyRange(
            HttpServletRequest request,
            Resource resource,
            Action action,
            Set<String> allowedRanges
    ) {
        if (isSystemAdmin(request)) {
            return true;
        }
        if (allowedRanges == null || allowedRanges.isEmpty()) {
            return false;
        }
        String required = resource.name() + ":" + action.name() + ":";
        Set<String> normalizedRanges = allowedRanges.stream()
                .map(range -> range == null ? "" : range.trim().toUpperCase(Locale.ROOT))
                .filter(range -> !range.isEmpty())
                .collect(Collectors.toSet());
        return getGrantedPermissions(request).stream()
                .filter(permission -> permission.startsWith(required))
                .map(permission -> permission.substring(required.length()).trim().toUpperCase(Locale.ROOT))
                .anyMatch(normalizedRanges::contains);
    }

    /**
     * 조직 Objective 생성·수정·취소 등 — Redis 에 {@code GOAL:CREATE:TEAM|COMPANY} 또는
     * 동일 범위의 {@code GOAL:UPDATE:TEAM|COMPANY} 가 있으면 true (역할에서 CREATE 없이 UPDATE 만 부여된 경우 대응).
     */
    public boolean canCreateOrganizationScopedGoal(HttpServletRequest request) {
        if (hasPermissionInAnyRange(request, Resource.GOAL, Action.CREATE, Set.of("TEAM", "COMPANY"))) {
            return true;
        }
        return hasPermissionInAnyRange(request, Resource.GOAL, Action.UPDATE, Set.of("TEAM", "COMPANY"));
    }

    private List<String> getGrantedPermissions(HttpServletRequest request) {
        String memberPositionId = request.getHeader("X-User-MemberPositionId");
        if (memberPositionId == null || memberPositionId.isEmpty()) {
            return List.of();
        }
        String permissionStr = permissionRedisTemplate.opsForValue().get("PERMISSION:" + memberPositionId);
        if (permissionStr == null || permissionStr.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(permissionStr.split(","))
                .map(String::trim)
                .filter(permission -> !permission.isEmpty())
                .toList();
    }
}
