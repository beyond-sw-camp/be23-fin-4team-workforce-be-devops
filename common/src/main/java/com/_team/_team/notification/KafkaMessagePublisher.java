package com._team._team.notification;

import com._team._team.redis.MessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public KafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String topic, String message) {
        kafkaTemplate.send(topic, message);
        log.info("Kafka 발행 성공 topic: {}", topic);
    }

    // NotificationMessage 객체 발행용
    public void publish(String topic, Object message) {
        kafkaTemplate.send(topic, message);
        log.info("Kafka 발행 성공 topic: {}", topic);
    }
}