package com._team._team.memberchat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기존 챗봇 설정(chat.*)과 충돌을 피하기 위해 prefix 를 "member-chat" 으로 분리.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "member-chat")
public class MemberChatProperties {

    private boolean enabled;
    private final Websocket websocket = new Websocket();
    private final Stomp stomp = new Stomp();
    private final Redis redis = new Redis();
    private final Files files = new Files();
    private final RateLimit rateLimit = new RateLimit();

    @Getter @Setter
    public static class Websocket {
        private String endpoint;
        private List<String> allowedOrigins;
    }

    @Getter @Setter
    public static class Stomp {
        private String appPrefix;
        private String topicPrefix;
        private String userPrefix;
    }

    @Getter @Setter
    public static class Redis {
        private String channelPrefix;
    }

    @Getter @Setter
    public static class Files {
        private String bucket;
        private long maxSizeMb;
        private List<String> allowedMimes;
        private int presignedTtlSeconds;
    }

    @Getter @Setter
    public static class RateLimit {
        private int messagesPerMinute;
        private int burst;
    }
}
