package com.jypark.tps1000.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품. 축 2(캐시 계층화)의 조회 대상 — 캐시 실험에 필요한 최소 필드만 둔다.
 * 가격은 원 단위 정수(long). 소수점 통화가 아니므로 BigDecimal은 과한 선택.
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 캐시 무효화 검증용 — 수정 후 조회가 새 값을 보는지 확인하는 기준. */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Product(String name, long price) {
        this.name = name;
        this.price = price;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static Product create(String name, long price) {
        return new Product(name, price);
    }

    public void update(String name, long price) {
        this.name = name;
        this.price = price;
        this.updatedAt = LocalDateTime.now();
    }
}
