package com.jypark.tps1000.order.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_CREATED_DLQ = "order.created.dlq";

    /**
     * 파티션 12: 컨슈머 동시성 상한. 동시성 스케일 실험(측정 1-c)을 위해 3 → 12로 증설
     * (KafkaAdmin이 부팅 시 기존 토픽의 파티션을 선언 수까지 늘려준다 — 줄이는 건 불가).
     * replicas 1: 로컬 단일 브로커(KRaft) 한계 — docs/decisions.md 4번 참고.
     */
    @Bean
    public KafkaAdmin.NewTopics orderTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ORDER_CREATED_TOPIC).partitions(12).replicas(1).build(),
                TopicBuilder.name(ORDER_CREATED_DLQ).partitions(1).replicas(1).build()
        );
    }

    /**
     * 재시도 + DLQ 정책 (docs/decisions.md 9번):
     * 1초 고정 백오프 × 2회 재시도(총 3회 시도) 후 order.created.dlq로 발행.
     * Boot가 이 CommonErrorHandler 빈을 기본 리스너 컨테이너 팩토리에 자동 연결한다.
     * 파티션 -1: DLQ는 1파티션이라 원본 파티션(0~2)을 그대로 쓰면 존재하지 않을 수 있음 → 프로듀서에 위임.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(ORDER_CREATED_DLQ, -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
