package com._team._team.memberchat.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * member-chat 전용 Redis Bean.
 * 공용 RedisConfig 에는 StringRedisTemplate 이 없고 RedisTemplate<String,String> 도
 * 여러 qualifier 로 분산되어 있어, 여기서 전용 Connection + Template 을 별도로 정의한다.
 *
 *  - DB 번호: 4 (기존 0~3 과 충돌 안 함. 필요 시 yml 에서 override)
 *  - Bean name: memberChatRedisConnectionFactory / memberChatRedisTemplate
 *
 * Pub/Sub 은 DB 번호를 공유해도 문제없지만, 카운터/락용 키와 이벤트 채널을 한 인스턴스에 집중시켜
 * 운영 단순화 + 다른 서비스 키와 격리를 확보한다.
 */
@Configuration
public class MemberChatRedisConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${member-chat.redis.database:4}")
    private int database;

    @Bean
    @Qualifier("memberChatRedisConnectionFactory")
    public RedisConnectionFactory memberChatRedisConnectionFactory() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration();
        cfg.setHostName(host);
        cfg.setPort(port);
        cfg.setDatabase(database);
        return new LettuceConnectionFactory(cfg);
    }

    @Bean
    @Qualifier("memberChatRedisTemplate")
    public StringRedisTemplate memberChatRedisTemplate(
            @Qualifier("memberChatRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
