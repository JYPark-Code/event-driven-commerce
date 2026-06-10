package com.jypark.tps1000.backoffice.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 월별 상품 정산 결과 (축 3 — Spring Batch 산출물).
 * 한 행 = (정산월, 상품) 단위 집계. 상품명·단가를 스냅샷으로 박제한다 —
 * 정산 후 상품 가격이 바뀌어도 과거 정산 수치가 흔들리면 안 되기 때문.
 */
@Entity
@Table(name = "settlements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"settlement_month", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** yyyy-MM (예: 2026-06) */
    @Column(name = "settlement_month", nullable = false, length = 7)
    private String settlementMonth;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private long totalQuantity;

    /** 원 단위 정수: 정산 시점 상품 단가 × 수량 합 */
    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Settlement(String settlementMonth, Long productId, String productName,
                       long totalQuantity, long totalAmount) {
        this.settlementMonth = settlementMonth;
        this.productId = productId;
        this.productName = productName;
        this.totalQuantity = totalQuantity;
        this.totalAmount = totalAmount;
        this.createdAt = LocalDateTime.now();
    }

    public static Settlement of(String settlementMonth, Long productId, String productName,
                                long totalQuantity, long totalAmount) {
        return new Settlement(settlementMonth, productId, productName, totalQuantity, totalAmount);
    }
}
