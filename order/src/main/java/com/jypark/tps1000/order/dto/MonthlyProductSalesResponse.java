package com.jypark.tps1000.order.dto;

/**
 * 월별 상품 판매 집계 한 행 (내부 API 응답 — MSA 3b 선행 과제).
 * 컨슈머(backoffice)는 이 클래스를 공유하지 않고 JSON 스키마로 계약한다.
 */
public record MonthlyProductSalesResponse(
        Long productId,
        long totalQuantity
) {
}
