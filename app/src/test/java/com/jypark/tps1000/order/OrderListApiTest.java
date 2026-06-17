package com.jypark.tps1000.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.jypark.tps1000.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 목록 조회 API(GET /api/orders) 통합 테스트.
 *
 * 통합 테스트 DB는 클래스 간 공유되며 롤백되지 않으므로(다른 테스트가 생성한 주문이 섞인다)
 * totalElements 절대값에 의존하지 않고, "생성한 만큼 이상" / "필터 결과가 전체 이하" /
 * "정렬·페이징 불변식" 같은 상대적 단언으로 검증한다.
 */
class OrderListApiTest extends IntegrationTestBase {

    @Autowired
    OrderRepository orderRepository;

    private int expectedTotalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    /** content 전체가 createdAt 내림차순 + id 내림차순으로 안정 정렬되어 있는지 검증한다. */
    private void assertSortedDescending(JsonNode content) {
        for (int i = 1; i < content.size(); i++) {
            JsonNode prev = content.get(i - 1);
            JsonNode cur = content.get(i);
            LocalDateTime prevCreated = LocalDateTime.parse(prev.get("createdAt").asText());
            LocalDateTime curCreated = LocalDateTime.parse(cur.get("createdAt").asText());
            int cmp = prevCreated.compareTo(curCreated);
            assertThat(cmp).as("createdAt 내림차순").isGreaterThanOrEqualTo(0);
            if (cmp == 0) {
                assertThat(prev.get("orderId").asLong())
                        .as("동일 createdAt 시 id 내림차순")
                        .isGreaterThan(cur.get("orderId").asLong());
            }
        }
    }

    @Test
    @DisplayName("기본 페이징: page/size 미지정 시 page=0, size=20, totalPages는 totalElements 기준 올림")
    void listOrders_defaultPaging() throws Exception {
        for (int i = 0; i < 3; i++) {
            orderRepository.save(Order.create(100L + i, 1));
        }

        MvcResult result = mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        long total = root.get("totalElements").asLong();
        assertThat(total).isGreaterThanOrEqualTo(3);
        assertThat(root.get("totalPages").asInt()).isEqualTo(expectedTotalPages(total, 20));
        assertThat(root.get("content").size()).isLessThanOrEqualTo(20);

        // content 원소 스키마가 OrderResponse와 동일한지 확인
        JsonNode first = root.get("content").get(0);
        assertThat(first.has("orderId")).isTrue();
        assertThat(first.has("orderKey")).isTrue();
        assertThat(first.has("productId")).isTrue();
        assertThat(first.has("quantity")).isTrue();
        assertThat(first.has("status")).isTrue();
        assertThat(first.has("createdAt")).isTrue();
    }

    @Test
    @DisplayName("status 필터: 해당 상태 주문만 content에 포함되고 totalElements/totalPages가 필터 기준으로 계산된다")
    void listOrders_statusFilter() throws Exception {
        // CREATED 1건, COMPLETED 2건 생성
        orderRepository.save(Order.create(200L, 1));
        for (int i = 0; i < 2; i++) {
            Order completed = Order.create(201L + i, 1);
            completed.complete();
            orderRepository.save(completed);
        }

        long totalAll;
        MvcResult all = mockMvc.perform(get("/api/orders").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        totalAll = objectMapper.readTree(all.getResponse().getContentAsString())
                .get("totalElements").asLong();

        MvcResult result = mockMvc.perform(get("/api/orders")
                        .param("size", "100")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = root.get("content");
        for (JsonNode order : content) {
            assertThat(order.get("status").asText()).isEqualTo("COMPLETED");
        }
        long totalCompleted = root.get("totalElements").asLong();
        assertThat(totalCompleted).isGreaterThanOrEqualTo(2);
        assertThat(totalCompleted).isLessThanOrEqualTo(totalAll);
        assertThat(root.get("totalPages").asInt()).isEqualTo(expectedTotalPages(totalCompleted, 100));
        assertSortedDescending(content);
    }

    @Test
    @DisplayName("size가 100 초과면 100으로 클램프되고 content 길이는 100 이하")
    void listOrders_sizeClampedTo100() throws Exception {
        orderRepository.save(Order.create(300L, 1));

        MvcResult result = mockMvc.perform(get("/api/orders").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("content").size()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("전체 주문 수보다 큰 page 요청: 200 + 빈 content, totalElements/totalPages는 전체 기준 유지")
    void listOrders_pageBeyondTotal() throws Exception {
        orderRepository.save(Order.create(400L, 1));

        MvcResult result = mockMvc.perform(get("/api/orders")
                        .param("page", "100000")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(100000))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        long total = root.get("totalElements").asLong();
        assertThat(total).isGreaterThanOrEqualTo(1);
        assertThat(root.get("totalPages").asInt()).isEqualTo(expectedTotalPages(total, 20));
    }

    @Test
    @DisplayName("서로 다른 createdAt의 주문들이 createdAt 내림차순(동일 시 id 내림차순)으로 정렬되어 반환된다")
    void listOrders_sortedByCreatedAtDesc() throws Exception {
        List<Long> createdIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Order saved = orderRepository.save(Order.create(500L + i, 1));
            createdIds.add(saved.getId());
            Thread.sleep(5); // createdAt 구분
        }

        MvcResult result = mockMvc.perform(get("/api/orders").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        assertSortedDescending(content);

        // 방금 생성한 주문들은 응답에서 id 내림차순(=최신 생성순)으로 나타나야 한다
        List<Long> seen = new ArrayList<>();
        for (JsonNode order : content) {
            long id = order.get("orderId").asLong();
            if (createdIds.contains(id)) {
                seen.add(id);
            }
        }
        List<Long> expected = new ArrayList<>(createdIds);
        expected.sort((a, b) -> Long.compare(b, a)); // 내림차순
        assertThat(seen).isEqualTo(expected);
    }

    @Test
    @DisplayName("존재하지 않는 status 이름은 400")
    void listOrders_invalidStatus() throws Exception {
        mockMvc.perform(get("/api/orders").param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("status는 대소문자까지 정확히 일치해야 한다 (소문자는 400)")
    void listOrders_statusCaseSensitive() throws Exception {
        mockMvc.perform(get("/api/orders").param("status", "created"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("page가 음수면 400")
    void listOrders_negativePage() throws Exception {
        mockMvc.perform(get("/api/orders").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("size가 1 미만이면 400")
    void listOrders_sizeBelowOne() throws Exception {
        mockMvc.perform(get("/api/orders").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("page가 int 범위를 초과하는 문자열이면 400")
    void listOrders_pageOverflow() throws Exception {
        mockMvc.perform(get("/api/orders").param("page", "9999999999999999999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET 외 메서드는 405")
    void listOrders_methodNotAllowed() throws Exception {
        mockMvc.perform(put("/api/orders"))
                .andExpect(status().isMethodNotAllowed());
    }
}
