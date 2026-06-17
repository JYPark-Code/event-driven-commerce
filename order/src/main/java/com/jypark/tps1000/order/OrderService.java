package com.jypark.tps1000.order;

import com.jypark.tps1000.order.dto.AsyncOrderAcceptedResponse;
import com.jypark.tps1000.order.dto.CreateOrderRequest;
import com.jypark.tps1000.order.dto.OrderPageResponse;
import com.jypark.tps1000.order.dto.OrderResponse;
import com.jypark.tps1000.order.event.OrderCreatedEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /** 페이지 크기 상한 — 초과 요청은 이 값으로 클램프한다(스펙). */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 주문 목록 조회(읽기 전용). status가 주어지면 해당 상태만 필터한다.
     * 정렬은 항상 createdAt 내림차순 + 보조 키 id 내림차순으로 고정해 매 호출 순서를 안정화한다
     * (클라이언트 sort 파라미터는 받지 않으므로 영향이 없다).
     * page/size 검증 실패는 400, 정의되지 않은 status는 400으로 매핑한다.
     */
    @Transactional(readOnly = true)
    public OrderPageResponse listOrders(int page, int size, String status) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        }
        if (size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be >= 1");
        }
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);

        OrderStatus statusFilter = parseStatus(status);

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, effectiveSize, sort);

        Page<Order> result = (statusFilter == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(statusFilter, pageable);
        return OrderPageResponse.from(result);
    }

    /** status는 OrderStatus 이름과 대소문자까지 정확히 일치해야 한다. 불일치 시 400. */
    private OrderStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status: " + status);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("order not found: " + orderId));
        return OrderResponse.from(order);
    }
}
