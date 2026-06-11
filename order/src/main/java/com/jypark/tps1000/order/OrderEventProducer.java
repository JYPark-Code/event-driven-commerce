package com.jypark.tps1000.order;

import com.jypark.tps1000.order.config.KafkaConfig;
import com.jypark.tps1000.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    /**
     * 브로커 ack을 기다리지 않고 프로듀서 버퍼에 넘긴 뒤 바로 리턴 — 접수 응답시간이 측정 포인트.
     * 트레이드오프: 브로커 장애 시 202를 받고도 주문이 유실될 수 있다(콜백 로그로만 감지).
     * 운영이라면 transactional outbox로 보완할 지점 — docs/decisions.md 7번.
     */
    public void publish(OrderCreatedEvent event) {
        kafkaTemplate.send(KafkaConfig.ORDER_CREATED_TOPIC, event.orderKey(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("주문 이벤트 발행 실패 orderKey={}", event.orderKey(), ex);
                    }
                });
    }
}
