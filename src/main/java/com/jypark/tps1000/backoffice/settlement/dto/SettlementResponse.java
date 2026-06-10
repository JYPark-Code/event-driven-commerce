package com.jypark.tps1000.backoffice.settlement.dto;

import com.jypark.tps1000.backoffice.settlement.Settlement;

public record SettlementResponse(
        String settlementMonth,
        Long productId,
        String productName,
        long totalQuantity,
        long totalAmount) {

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getSettlementMonth(),
                settlement.getProductId(),
                settlement.getProductName(),
                settlement.getTotalQuantity(),
                settlement.getTotalAmount());
    }
}
