package com._team._team.util;

import com._team._team.annotation.Action;
import com._team._team.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Component
public class PermissionUtils {

    private final RedisTemplate<String, String> permissionRedisTemplate;

    public PermissionUtils(
            @Qualifier("permissionInventory") RedisTemplate<String, String> permissionRedisTemplate) {
        this.permissionRedisTemplate = permissionRedisTemplate;
    }

    // permissionRange 추출
    public String getPermissionRange(String memberPositionId, Resource resource, Action action) {
        String permissionStr = permissionRedisTemplate.opsForValue()
                .get("PERMISSION:" + memberPositionId);

        if (permissionStr == null) return "COMPANY"; // 시스템 관리자

        return Arrays.stream(permissionStr.split(","))
                .filter(p -> p.startsWith(resource.name() + ":" + action.name() + ":"))
                .findFirst()
                .map(p -> p.split(":")[2])
                .orElse("SELF");
    }

    // 범위별 데이터 조회 공통 처리
    public <T> List<T> getDataByRange(
            String memberPositionId,
            Resource resource,
            Action action,
            Supplier<List<T>> companyQuery,    // COMPANY 범위 쿼리
            Supplier<List<T>> teamQuery,       // TEAM/DEPARTMENT 범위 쿼리
            Supplier<List<T>> selfQuery        // SELF 범위 쿼리
    ) {
        String range = getPermissionRange(memberPositionId, resource, action);

        return switch (range) {
            case "COMPANY" -> companyQuery.get();
            case "TEAM", "DEPARTMENT" -> teamQuery.get();
            default -> selfQuery.get();
        };
    }
}