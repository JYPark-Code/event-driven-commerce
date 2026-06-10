package com.jypark.tps1000.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jypark.tps1000.order.dto.CreateOrderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비동기 주문 API 통합 테스트. docker compose 인프라(MySQL 3307, Kafka 9092)가 떠 있어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderAsyncApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("주문 접수(비동기): 202 + orderKey(UUID) 즉시 반환")
    void placeOrderAsync_returnsAcceptedImmediately() throws Exception {
        var request = new CreateOrderRequest(1L, 2);

        mockMvc.perform(post("/api/orders/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderKey").isString());
    }

    @Test
    @DisplayName("수량 0 이하면 400 (발행 전 검증)")
    void placeOrderAsync_invalidQuantity() throws Exception {
        var request = new CreateOrderRequest(1L, 0);

        mockMvc.perform(post("/api/orders/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
