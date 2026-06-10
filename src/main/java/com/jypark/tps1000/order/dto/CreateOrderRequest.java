package com.jypark.tps1000.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long productId,
        @Min(1) int quantity
) {
}
