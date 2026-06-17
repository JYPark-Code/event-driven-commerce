package com.jypark.tps1000.order.dto;

import com.jypark.tps1000.order.Order;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 주문 목록 조회(GET /api/orders) 응답. content는 단건 조회와 동일한 OrderResponse 형태로 담고,
 * 페이징 메타(page/size/totalElements/totalPages)를 함께 노출한다.
 * totalPages는 Page.getTotalPages() 계약을 따른다 — totalElements가 0이면 0.
 */
public record OrderPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static OrderPageResponse from(Page<Order> page) {
        return new OrderPageResponse(
                page.getContent().stream().map(OrderResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
