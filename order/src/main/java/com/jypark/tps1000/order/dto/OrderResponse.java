package com.jypark.tps1000.order.dto;

import com.jypark.tps1000.order.Order;
import com.jypark.tps1000.order.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        String orderKey,    // 비동기 주문만 보유 (동기 주문은 null)
        Long productId,
        int quantity,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderKey(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
