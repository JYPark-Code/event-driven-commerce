package com.jypark.tps1000.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaConfig {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_CREATED_DLQ = "order.created.dlq";

    /**
     * 파티션 3: 컨슈머 동시성(concurrency=3)과 맞춤. 파티션 수 튜닝은 부하 측정 단계에서.
     * replicas 1: 로컬 단일 브로커(KRaft) 한계 — docs/decisions.md 4번 참고.
     */
    @Bean
    public KafkaAdmin.NewTopics orderTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ORDER_CREATED_TOPIC).partitions(3).replicas(1).build(),
                TopicBuilder.name(ORDER_CREATED_DLQ).partitions(1).replicas(1).build()
        );
    }
}
