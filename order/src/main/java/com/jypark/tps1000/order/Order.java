package com.jypark.tps1000.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 주문. ORDER는 SQL 예약어라 테이블명은 orders.
 * 상품 도메인(축 2)과의 결합을 피하려고 productId만 보관한다(FK 없음) — 데모 범위 최소화.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 비동기 주문의 멱등성 키(UUID). 접수 시점에 발급되어 클라이언트 조회 키로도 쓰인다.
     * 유니크 제약이 중복 소비의 최종 방어선 (동기 주문은 null — MySQL 유니크는 null 중복 허용).
     */
    @Column(unique = true, updatable = false, length = 36)
    private String orderKey;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Order(String orderKey, Long productId, int quantity) {
        this.orderKey = orderKey;
        this.productId = productId;
        this.quantity = quantity;
        this.status = OrderStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    public static Order create(Long productId, int quantity) {
        return new Order(null, productId, quantity);
    }

    public static Order fromEvent(String orderKey, Long productId, int quantity) {
        return new Order(orderKey, productId, quantity);
    }

    /** 후속 처리(알림 등)까지 끝났을 때 컨슈머가 호출. */
    public void complete() {
        this.status = OrderStatus.COMPLETED;
    }

    /** CREATED 상태에서만 취소 가능. 그 외 상태면 false를 반환하고 상태를 바꾸지 않는다. */
    public boolean isCancelable() {
        return this.status == OrderStatus.CREATED;
    }

    /** 주문 취소: 상태만 CANCELED로 전이한다(환불·재고 복원 등 부수효과 없음). */
    public void cancel() {
        this.status = OrderStatus.CANCELED;
    }
}
