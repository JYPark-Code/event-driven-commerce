package com.jypark.tps1000.order;

import com.jypark.tps1000.order.config.KafkaConfig;
import com.jypark.tps1000.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 배치 컨슈머 (측정 1-c-ii). 레코드 단위 처리(이벤트당 트랜잭션 커밋)가 ~200건/s 병목이라
 * 폴링 배치 단위로: 기처리 필터 SELECT 1회 + multi-row INSERT 1회 + 커밋 1회로 비용을 상각한다.
 *
 * 멱등 처리 (docs/decisions.md 8번 유지):
 * 1차 — 배치 내 중복 제거 + findExistingOrderKeys(IN) 사전 필터.
 * 2차 — INSERT IGNORE (order_key 유니크): 필터를 뚫는 경합도 조용히 스킵.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final OrderJdbcRepository orderJdbcRepository;
    private final OrderNotificationService notificationService;

    @KafkaListener(topics = KafkaConfig.ORDER_CREATED_TOPIC,
            concurrency = "${app.order-consumer.concurrency:3}",
            batch = "true")
    public void consume(List<OrderCreatedEvent> events) {
        Set<String> processed = new HashSet<>(
                orderRepository.findExistingOrderKeys(events.stream().map(OrderCreatedEvent::orderKey).toList()));
        List<Order> pending = new ArrayList<>(events.size());

        for (int i = 0; i < events.size(); i++) {
            OrderCreatedEvent event = events.get(i);
            if (!processed.add(event.orderKey())) {
                log.info("중복 주문 이벤트 스킵 orderKey={}", event.orderKey());
                continue;
            }
            try {
                notificationService.notifyOrderAccepted(event);
            } catch (Exception ex) {
                // 실패 레코드 앞까지는 저장을 완결시켜야 한다 — 에러 핸들러가 index 앞 레코드의
                // 오프셋을 커밋하므로, 여기서 저장하지 않으면 그 주문들은 영영 유실된다.
                orderJdbcRepository.insertAllIgnoreDuplicates(pending);
                throw new BatchListenerFailedException("주문 후속 처리 실패", ex, i);
            }
            Order order = Order.fromEvent(event.orderKey(), event.productId(), event.quantity());
            order.complete();
            pending.add(order);
        }
        orderJdbcRepository.insertAllIgnoreDuplicates(pending);
    }
}
