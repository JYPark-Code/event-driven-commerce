package com.jypark.tps1000.order.dto;

/**
 * 비동기 주문 접수 응답(202). 처리 결과는 GET /api/orders/async/{orderKey}로 폴링.
 */
public record AsyncOrderAcceptedResponse(String orderKey) {
}
