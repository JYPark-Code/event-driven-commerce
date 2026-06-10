package com.jypark.tps1000.order.dto;

import com.jypark.tps1000.order.Order;
import com.jypark.tps1000.order.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        Long productId,
        int quantity,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
