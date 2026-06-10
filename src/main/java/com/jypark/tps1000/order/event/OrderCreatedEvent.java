package com.jypark.tps1000.order.event;

/**
 * 주문 접수 이벤트. orderKey가 멱등성 키이자 Kafka 파티션 키.
 * 같은 orderKey → 같은 파티션 → 같은 컨슈머가 순차 처리 (중복 이벤트가 동시에 처리될 일 없음).
 */
public record OrderCreatedEvent(
        String orderKey,
        Long productId,
        int quantity
) {
}
