package com.jypark.tps1000.order;

import com.jypark.tps1000.order.dto.CreateOrderRequest;
import com.jypark.tps1000.order.dto.OrderResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

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

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("order not found: " + orderId));
        return OrderResponse.from(order);
    }
}
