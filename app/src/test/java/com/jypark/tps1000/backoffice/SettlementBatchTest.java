package com.jypark.tps1000.backoffice;

import com.fasterxml.jackson.databind.JsonNode;
import com.jypark.tps1000.IntegrationTestBase;
import com.jypark.tps1000.backoffice.auth.dto.LoginRequest;
import com.jypark.tps1000.order.Order;
import com.jypark.tps1000.order.OrderRepository;
import com.jypark.tps1000.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 월별 정산 잡 검증. DB가 테스트 간 보존되므로 매번 새 상품을 만들어
 * 그 상품의 정산 행만 단언한다 (다른 테스트가 만든 주문과 격리).
 */
class SettlementBatchTest extends IntegrationTestBase {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("정산 잡: 당월 주문을 상품별로 집계하고, 재실행해도 중복 없이 덮어쓴다")
    void settlementJob_aggregatesAndIsIdempotent() throws Exception {
        Product productA = productRepository.save(Product.create("정산상품A-" + suffix(), 1_000L));
        Product productB = productRepository.save(Product.create("정산상품B-" + suffix(), 2_500L));
        orderRepository.save(Order.create(productA.getId(), 2));
        orderRepository.save(Order.create(productA.getId(), 3));
        orderRepository.save(Order.create(productB.getId(), 4));

        String month = YearMonth.now().toString();
        String adminToken = loginAdmin();

        mockMvc.perform(post("/api/admin/settlements/run")
                        .param("month", month)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        objectMapper.readTree(result.getResponse().getContentAsString())
                                .get("jobStatus").asText()).isEqualTo("COMPLETED"));

        JsonNode rowA = findSettlementRow(month, adminToken, productA.getId());
        assertThat(rowA.get("totalQuantity").asLong()).isEqualTo(5);
        assertThat(rowA.get("totalAmount").asLong()).isEqualTo(5 * 1_000L);
        assertThat(rowA.get("productName").asText()).isEqualTo(productA.getName());

        JsonNode rowB = findSettlementRow(month, adminToken, productB.getId());
        assertThat(rowB.get("totalQuantity").asLong()).isEqualTo(4);
        assertThat(rowB.get("totalAmount").asLong()).isEqualTo(4 * 2_500L);

        // 재실행: clear 스텝이 기존 행을 지우므로 같은 달을 다시 정산해도 행이 늘지 않고 수치 동일
        mockMvc.perform(post("/api/admin/settlements/run")
                        .param("month", month)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertThat(countSettlementRows(month, adminToken, productA.getId())).isEqualTo(1);
        assertThat(findSettlementRow(month, adminToken, productA.getId()).get("totalQuantity").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("정산 트리거: 토큰 없으면 401 (ADMIN 전용 경로)")
    void settlementRun_withoutToken_unauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/run").param("month", "2026-06"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정산 트리거: month 형식이 yyyy-MM이 아니면 400")
    void settlementRun_invalidMonth_badRequest() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/run")
                        .param("month", "2026/06")
                        .header("Authorization", "Bearer " + loginAdmin()))
                .andExpect(status().isBadRequest());
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin1234!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode settlements(String month, String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/settlements")
                        .param("month", month)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findSettlementRow(String month, String adminToken, Long productId) throws Exception {
        for (JsonNode row : settlements(month, adminToken)) {
            if (row.get("productId").asLong() == productId) {
                return row;
            }
        }
        throw new AssertionError("settlement row not found for productId=" + productId);
    }

    private long countSettlementRows(String month, String adminToken, Long productId) throws Exception {
        long count = 0;
        for (JsonNode row : settlements(month, adminToken)) {
            if (row.get("productId").asLong() == productId) {
                count++;
            }
        }
        return count;
    }
}
