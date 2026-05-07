package com._team._team.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Field;
import java.time.Duration;

/**
 * Redis 기반 Spring Cache 설정 - 캐시별 TTL/직렬화 정책만 담당
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(
            @Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory cf) {

        // Spring 무인자 GenericJackson2JsonRedisSerializer 사용
        // - 내부 TypeResolverBuilder(EVERYTHING + PROPERTY) 가 outer 컬렉션까지 wrap (List/Map 안전)
        // - 우리 mapper 로 만들면 outer wrap 누락돼 deserialize 에러 발생
        // - reflection 으로 내부 ObjectMapper 꺼내서 JavaTimeModule 만 추가 (LocalDate/LocalDateTime 지원)
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        try {
            Field mapperField = GenericJackson2JsonRedisSerializer.class.getDeclaredField("mapper");
            mapperField.setAccessible(true);
            ObjectMapper internalMapper = (ObjectMapper) mapperField.get(valueSerializer);
            internalMapper.registerModule(new JavaTimeModule());
            internalMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Spring Data Redis 버전 변경으로 mapper 필드 접근 실패", e);
        }

        // 키 prefix 에 직렬화 포맷 버전 포함
        // - 직렬화 포맷 변경 시 옛 키와 충돌 방지 (옛 키는 TTL 만료 후 자연 삭제)
        RedisCacheConfiguration defaultCfg = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .computePrefixWith(cacheName -> "v4:" + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(cf)
                .cacheDefaults(defaultCfg)
                .withCacheConfiguration(
                        "overtimeUsageStatus",
                        defaultCfg.entryTtl(Duration.ofMinutes(5)))
                // member-service 직원 list - 변동 빈도 낮음 + 5분
                .withCacheConfiguration(
                        "membersByCompany",
                        defaultCfg.entryTtl(Duration.ofMinutes(5)))
                // 회사 마스터 데이터 (휴가종류 / 공휴일 / 급여항목 템플릿) - 거의 변동 없음 10분
                .withCacheConfiguration(
                        "companyLeaveTypes",
                        defaultCfg.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration(
                        "companyHolidays",
                        defaultCfg.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration(
                        "salaryItemTemplates",
                        defaultCfg.entryTtl(Duration.ofMinutes(10)))
                .build();
    }
}
