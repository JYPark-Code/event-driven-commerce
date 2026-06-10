package com.jypark.tps1000.product.dto;

import com.jypark.tps1000.product.Product;

import java.time.LocalDateTime;

/**
 * 상품 조회 응답이자 캐시에 저장되는 값.
 * 엔티티 대신 이 DTO를 캐싱한다 — 영속성 컨텍스트 밖에서 안전하고, 직렬화 형태가 API 응답과 같아 단순하다.
 */
public record ProductResponse(
        Long productId,
        String name,
        long price,
        LocalDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getUpdatedAt());
    }
}
