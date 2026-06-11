package com.jypark.tps1000.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class ProductKafkaConfig {

    public static final String PRODUCT_CHANGED_TOPIC = "product.changed";

    /**
     * 파티션 3: 키 = productId라 같은 상품의 변경은 같은 파티션에서 순차 처리된다(복제본 순서 보장의 근거).
     * 상품 변경은 저빈도라 주문 토픽(12)만큼의 동시성 여지가 필요 없다. replicas 1: 단일 브로커(decisions.md 4번).
     */
    @Bean
    public KafkaAdmin.NewTopics productTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(PRODUCT_CHANGED_TOPIC).partitions(3).replicas(1).build());
    }
}
