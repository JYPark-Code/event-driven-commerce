package com.jypark.tps1000.order;

import com.jypark.tps1000.order.dto.AsyncOrderAcceptedResponse;
import com.jypark.tps1000.order.dto.CreateOrderRequest;
import com.jypark.tps1000.order.dto.OrderResponse;
import com.jypark.tps1000.order.event.OrderCreatedEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    /**
     * 동기 주문: 요청 → DB 저장 → 응답. (비교 기준선)
     * 비동기 버전(Kafka)에선 후속 처리를 컨슈머로 넘겨 응답 시간을 줄이는 게 측정 포인트.
     */
    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        Order order = Order.create(request.productId(), request.quantity());
        orderRepository.save(order);
        return OrderResponse.from(order);
    }

    /**
     * 비동기 주문: 요청 → Kafka 발행 → 즉시 202. DB 저장은 컨슈머가 수행 (측정 비교 대상 경로).
     * 요청 경로에 DB도 트랜잭션도 없는 것이 동기 버전과의 차이.
     */
    public AsyncOrderAcceptedResponse placeOrderAsync(CreateOrderRequest request) {
        String orderKey = UUID.randomUUID().toString();
        orderEventProducer.publish(new OrderCreatedEvent(orderKey, request.productId(), request.quantity()));
        return new AsyncOrderAcceptedResponse(orderKey);
    }

    /** 비동기 주문 폴링 조회. 컨슈머가 아직 처리 전이면 empty (컨트롤러에서 404). */
    @Transactional(readOnly = true)
    public Optional<OrderResponse> findOrderByKey(String orderKey) {
        return orderRepository.findByOrderKey(orderKey).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("order not found: " + orderId));
        return OrderResponse.from(order);
    }
}
