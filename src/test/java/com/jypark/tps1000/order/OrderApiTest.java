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
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 동기 주문 API 통합 테스트. docker compose 인프라(MySQL 3307)가 떠 있어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("주문 생성(동기): 201 + CREATED 상태로 저장되고, 조회로 다시 확인된다")
    void placeOrder_thenGet() throws Exception {
        var request = new CreateOrderRequest(1L, 2);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();

        long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("orderId").asLong();

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("수량 0 이하면 400")
    void placeOrder_invalidQuantity() throws Exception {
        var request = new CreateOrderRequest(1L, 0);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
