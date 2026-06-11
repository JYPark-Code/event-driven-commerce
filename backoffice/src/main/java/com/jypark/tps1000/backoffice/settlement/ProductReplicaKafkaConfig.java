package com.jypark.tps1000.backoffice.settlement;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;

@Configuration
public class ProductReplicaKafkaConfig {

    /**
     * 토픽 이름 문자열이 곧 모듈 간 계약 — product 모듈의 상수를 import하지 않는다.
     * 클래스(상수) 공유도 컴파일 의존이고, 그 의존을 없애는 것이 이 단계의 목적이다.
     */
    public static final String PRODUCT_CHANGED_TOPIC = "product.changed";

    /**
     * 전용 리스너 팩토리: 전역 컨슈머 설정(application.yml)이 주문 이벤트에 묶여 있어
     * (JsonDeserializer + default type = OrderCreatedEvent) 그대로 쓰면 상품 이벤트가
     * 주문 타입으로 역직렬화된다. 여기서는 String으로 받아 컨슈머가 직접 파싱한다 —
     * 컨슈머가 자기 계약 표현을 소유하는 MSA 패턴과도 일치.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> productReplicaContainerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        return factory;
    }
}
