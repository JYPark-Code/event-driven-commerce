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

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Order(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.status = OrderStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    public static Order create(Long productId, int quantity) {
        return new Order(productId, quantity);
    }
}
