package com.jypark.tps1000.order;

import com.jypark.tps1000.common.config.KafkaConfig;
import com.jypark.tps1000.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final OrderNotificationService notificationService;

    /**
     * 멱등 처리 전략 (docs/decisions.md 8번):
     * 1차 방어 — orderKey 존재 확인 후 스킵. 같은 orderKey는 같은 파티션에서 순차 처리되므로
     *   재전달(리밸런스, 오프셋 미커밋 재시작)로 인한 중복은 이 검사로 걸러진다.
     * 2차 방어 — DB 유니크 제약. 검사를 뚫는 희귀한 경합이면 제약 위반 → 에러 핸들러 재시도 →
     *   존재 확인에 걸려 스킵 (자기 치유, DLQ로 가지 않음).
     */
    // concurrency 프로퍼티화: 동시성 스케일 실험(측정 1-c)에서 재빌드 없이 변경하기 위함
    @KafkaListener(topics = KafkaConfig.ORDER_CREATED_TOPIC,
            concurrency = "${app.order-consumer.concurrency:3}")
    @Transactional
    public void consume(OrderCreatedEvent event) {
        if (orderRepository.existsByOrderKey(event.orderKey())) {
            log.info("중복 주문 이벤트 스킵 orderKey={}", event.orderKey());
            return;
        }
        Order order = Order.fromEvent(event.orderKey(), event.productId(), event.quantity());
        notificationService.notifyOrderAccepted(event);
        order.complete();
        orderRepository.save(order);
    }
}
