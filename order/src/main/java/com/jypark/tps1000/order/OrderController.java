package com.jypark.tps1000.order;

import com.jypark.tps1000.order.dto.AsyncOrderAcceptedResponse;
import com.jypark.tps1000.order.dto.CreateOrderRequest;
import com.jypark.tps1000.order.dto.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.placeOrder(request);
    }

    /**
     * 알려진 한계 — IDOR (decisions.md 25번): 순차 증가 ID라 타인 주문 열람이 가능하다.
     * 이 경로는 부하테스트 측정 대상이라 무인증이고(13번), 인증 주체가 없으면 "소유자" 개념도
     * 성립하지 않아 데모 범위에서 수용. 실서비스라면 인증 주체의 소유 검증 + 외부 노출 키는
     * 추측 불가 값(UUID) — 비동기 주문의 orderKey가 이미 그 패턴이다.
     */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        return orderService.getOrder(orderId);
    }

    /** 비동기 주문 접수: Kafka 발행 후 즉시 202. 동기(POST /api/orders)와의 응답시간 비교가 측정 포인트. */
    @PostMapping("/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AsyncOrderAcceptedResponse placeOrderAsync(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.placeOrderAsync(request);
    }

    /** 비동기 주문 폴링 조회. 컨슈머 처리 전이면 404 (미존재 키와 구분 안 됨 — 데모 범위에서 수용). */
    @GetMapping("/async/{orderKey}")
    public ResponseEntity<OrderResponse> getOrderByKey(@PathVariable String orderKey) {
        return orderService.findOrderByKey(orderKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
