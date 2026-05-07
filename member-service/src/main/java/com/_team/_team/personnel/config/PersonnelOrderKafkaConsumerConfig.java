package com._team._team.personnel.config;

import com._team._team.event.PersonnelOrderApprovedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * 인사발령 Kafka Consumer
 */
@Configuration
public class PersonnelOrderKafkaConsumerConfig {

    @Value("${spring.kafka.kafka-server}")
    private String kafkaServer;

    @Bean
    public ConsumerFactory<String, PersonnelOrderApprovedEvent> personnelOrderConsumerFactory() {
        JsonDeserializer<PersonnelOrderApprovedEvent> valueDeserializer =
                new JsonDeserializer<>(PersonnelOrderApprovedEvent.class, false);
        valueDeserializer.addTrustedPackages("*");
        valueDeserializer.setUseTypeHeaders(false);

        ErrorHandlingDeserializer<PersonnelOrderApprovedEvent> errorHandling =
                new ErrorHandlingDeserializer<>(valueDeserializer);

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServer);

        return new DefaultKafkaConsumerFactory<>(
                config,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                errorHandling
        );
    }

    @Bean(name = "personnelOrderListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PersonnelOrderApprovedEvent>
    personnelOrderListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PersonnelOrderApprovedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(personnelOrderConsumerFactory());
        return factory;
    }
}
