package com.jypark.tps1000.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jypark.tps1000.common.config.KafkaConfig;
import com.jypark.tps1000.order.dto.CreateOrderRequest;
import com.jypark.tps1000.order.event.OrderCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Autowired
    OrderRepository orderRepository;

    @MockitoSpyBean
    OrderNotificationService notificationService;

    @Test
    @DisplayName("주문 접수(비동기): 202 + orderKey 즉시 반환 → 컨슈머가 처리하면 COMPLETED로 조회된다")
    void placeOrderAsync_thenConsumerCompletes() throws Exception {
        var request = new CreateOrderRequest(1L, 2);

        MvcResult result = mockMvc.perform(post("/api/orders/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderKey").isString())
                .andReturn();

        String orderKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("orderKey").asText();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mockMvc.perform(get("/api/orders/async/" + orderKey))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.orderKey").value(orderKey))
                        .andExpect(jsonPath("$.productId").value(1))
                        .andExpect(jsonPath("$.quantity").value(2))
                        .andExpect(jsonPath("$.status").value("COMPLETED")));
    }

    @Test
    @DisplayName("같은 orderKey 이벤트가 두 번 와도 주문 1건, 후속 처리(알림)도 1회만 — 멱등성")
    void duplicateEvent_isProcessedOnce() {
        String orderKey = UUID.randomUUID().toString();
        var event = new OrderCreatedEvent(orderKey, 7L, 3);

        kafkaTemplate.send(KafkaConfig.ORDER_CREATED_TOPIC, orderKey, event);
        kafkaTemplate.send(KafkaConfig.ORDER_CREATED_TOPIC, orderKey, event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                org.assertj.core.api.Assertions.assertThat(orderRepository.existsByOrderKey(orderKey)).isTrue());

        // 두 번째 이벤트(같은 파티션, 순차 처리)까지 소비될 시간을 준 뒤 호출 횟수 확인
        verify(notificationService, after(3000).times(1))
                .notifyOrderAccepted(argThat(e -> e.orderKey().equals(orderKey)));
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
