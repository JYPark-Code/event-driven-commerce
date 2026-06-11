package com.jypark.tps1000.backoffice.settlement;

/** 정산 리더의 집계 단위: 해당 월 상품별 주문 수량 합 (orders GROUP BY product_id 결과 한 행). */
public record ProductSales(Long productId, long totalQuantity) {
}
