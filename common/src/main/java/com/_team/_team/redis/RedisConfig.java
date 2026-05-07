package com._team._team.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.port}")
    private int port;


    //리프레쉬토큰
    @Bean
    @Qualifier("rtInventory")
    public RedisConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        configuration.setDatabase(0);
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    @Qualifier("rtInventory")
    public RedisTemplate<String, String> redisTemplate(@Qualifier("rtInventory") RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }
    //메일 인증
    @Bean
    @Qualifier("emailInventory")
    public RedisConnectionFactory emailConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        configuration.setDatabase(1);
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    @Qualifier("emailInventory")
    public RedisTemplate<String, String> emailRedisTemplate(
            @Qualifier("emailInventory") RedisConnectionFactory emailConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setConnectionFactory(emailConnectionFactory);
        return redisTemplate;
    }
    // DB 2 - 권한 캐시
    @Bean
    @Qualifier("permissionInventory")
    public RedisConnectionFactory permissionConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        configuration.setDatabase(2);
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    @Qualifier("permissionInventory")
    public RedisTemplate<String, String> permissionRedisTemplate(
            @Qualifier("permissionInventory") RedisConnectionFactory permissionConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setConnectionFactory(permissionConnectionFactory);
        return redisTemplate;
    }
    // Pub/Sub - 알림 실시간 전송
    @Bean
    @Qualifier("pubSubRedisConnectionFactory")
    public RedisConnectionFactory pubSubRedisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        // Pub/Sub은 DB 번호 의미 없음 (기본 0)
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    @Qualifier("pubSubRedisTemplate")
    public RedisTemplate<String, String> pubSubRedisTemplate(
            @Qualifier("pubSubRedisConnectionFactory")
            RedisConnectionFactory pubSubRedisConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setConnectionFactory(pubSubRedisConnectionFactory);
        return redisTemplate;
    }

    @Bean
    public ChannelTopic notificationTopic() {
        return new ChannelTopic(RedisChannel.NOTIFICATION_CHANNEL);
    }

    @Bean
    @ConditionalOnBean(MessageListener.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            @Qualifier("pubSubRedisConnectionFactory")
            RedisConnectionFactory pubSubRedisConnectionFactory,
            MessageListener messageListener,
            ChannelTopic notificationTopic) {
        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();
        container.setConnectionFactory(pubSubRedisConnectionFactory);
        container.addMessageListener(messageListener, notificationTopic);
        return container;
    }
    // DB 3 - ShedLock
    @Bean
    @Qualifier("shedLockConnectionFactory")
    public RedisConnectionFactory shedLockConnectionFactory() {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        configuration.setDatabase(3);
        return new LettuceConnectionFactory(configuration);
    }

    // DB 4 - 공휴일 캐시
    @Bean
    @Qualifier("holidayInventory")
    public RedisConnectionFactory holidayConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        configuration.setDatabase(4);
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    @Qualifier("holidayInventory")
    public RedisTemplate<String, Object> holidayRedisTemplate(
            @Qualifier("holidayInventory") RedisConnectionFactory holidayConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setConnectionFactory(holidayConnectionFactory);
        return redisTemplate;
    }

    // DB 5 - 캐싱 처리
    // salary-service CacheConfig 가 RedisCacheManager 에 주입해서 사용
    @Bean
    @Qualifier("cacheRedisConnectionFactory")
    public RedisConnectionFactory cacheRedisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        configuration.setDatabase(5);
        return new LettuceConnectionFactory(configuration);
    }
}

