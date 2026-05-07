package com._team._team.redis;

public interface MessagePublisher {
    void publish(String topic, String message);
}