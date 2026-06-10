package com.jypark.tps1000.backoffice.settlement.dto;

public record SettlementRunResponse(String month, String jobStatus, long settledProductCount) {
}
