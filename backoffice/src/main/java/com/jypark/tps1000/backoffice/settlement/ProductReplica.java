package com.jypark.tps1000.backoffice.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 정산이 소유한 상품 데이터 로컬 복제본 (MSA 2단계 — product 모듈 직접 의존 해소).
 * product.changed 이벤트로 채워지며, 정산 배치는 이 테이블만 읽는다.
 *
 * PK = 원본 상품 ID (이벤트가 준 값 그대로, 채번 없음) → save가 자연스럽게 upsert이고,
 * at-least-once 재전달에도 멱등하다. 최종 일관성: 이벤트 지연·유실 구간만큼 원본과 어긋날 수 있다
 * (트레이드오프와 초기 적재 한계는 docs/decisions.md 18번).
 */
@Entity
@Table(name = "product_replica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReplica {

    @Id
    private Long productId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long price;

    /** 마지막 이벤트 반영 시각 — 복제 지연 진단용. */
    @Column(nullable = false)
    private LocalDateTime syncedAt;

    private ProductReplica(Long productId, String name, long price) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.syncedAt = LocalDateTime.now();
    }

    public static ProductReplica of(Long productId, String name, long price) {
        return new ProductReplica(productId, name, price);
    }
}
