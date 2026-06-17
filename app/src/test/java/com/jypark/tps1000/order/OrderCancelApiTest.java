package com.jypark.tps1000.order;

import com.jypark.tps1000.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 취소 API(POST /api/orders/{orderId}/cancel) 통합 테스트.
 *
 * CREATED 주문만 취소 가능(200, status=CANCELED). COMPLETED/FAILED/이미 CANCELED는 409,
 * 미존재 orderId는 404. 취소는 상태 전이만 수행하므로(환불·재고 복원 등 범위 밖) DB의 status만
 * 검증한다. 통합 DB는 클래스 간 공유되므로 각 케이스마다 주문을 직접 생성해 독립적으로 검증한다.
 */
class OrderCancelApiTest extends IntegrationTestBase {

    @Autowired
    OrderRepository orderRepository;

    private Long saveOrderWithStatus(OrderStatus status) {
        Order order = Order.create(900L, 1);
        switch (status) {
            case COMPLETED -> order.complete();
            case CANCELED -> order.cancel();
            // FAILED를 세팅하는 도메인 메서드는 없으므로(컨슈머 실패 경로 전용 상태) 테스트에서 직접 주입한다.
            case FAILED -> ReflectionTestUtils.setField(order, "status", OrderStatus.FAILED);
            case CREATED -> { /* 생성 직후 기본 상태 */ }
        }
        return orderRepository.save(order).getId();
    }

    @Test
    @DisplayName("CREATED 주문 취소: 200 + status=CANCELED로 변경되어 DB에 영속된다")
    void cancel_createdOrder_succeeds() throws Exception {
        Long orderId = saveOrderWithStatus(OrderStatus.CREATED);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.productId").value(900))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.createdAt").exists());

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("COMPLETED 주문 취소: 409, DB status는 그대로 COMPLETED")
    void cancel_completedOrder_conflict() throws Exception {
        Long orderId = saveOrderWithStatus(OrderStatus.COMPLETED);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isConflict());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("FAILED 주문 취소: 409, DB status는 그대로 FAILED")
    void cancel_failedOrder_conflict() throws Exception {
        Long orderId = saveOrderWithStatus(OrderStatus.FAILED);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isConflict());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.FAILED);
    }

    @Test
    @DisplayName("이미 CANCELED인 주문 취소: 409, DB status는 그대로 CANCELED")
    void cancel_alreadyCanceledOrder_conflict() throws Exception {
        Long orderId = saveOrderWithStatus(OrderStatus.CANCELED);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isConflict());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("존재하지 않는 orderId 취소: 404")
    void cancel_nonexistentOrder_notFound() throws Exception {
        mockMvc.perform(post("/api/orders/" + Long.MAX_VALUE + "/cancel"))
                .andExpect(status().isNotFound());
    }
}
