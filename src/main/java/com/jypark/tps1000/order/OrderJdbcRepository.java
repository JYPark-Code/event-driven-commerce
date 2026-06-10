package com.jypark.tps1000.order;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * 컨슈머 배치 저장 전용 (측정 1-c-ii).
 * JPA를 안 쓰는 이유: IDENTITY 전략에선 Hibernate가 INSERT JDBC 배칭을 비활성화한다.
 * INSERT IGNORE: 유니크(order_key) 충돌 = 이미 처리된 주문 → 조용히 스킵 (멱등 백스톱).
 *   FK 없는 테이블이라 IGNORE가 삼킬 다른 제약 위반이 사실상 없음.
 * rewriteBatchedStatements=true(datasource url)로 드라이버가 multi-row INSERT로 재작성 → 왕복 1회.
 */
@Repository
@RequiredArgsConstructor
public class OrderJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void insertAllIgnoreDuplicates(List<Order> orders) {
        if (orders.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT IGNORE INTO orders (order_key, product_id, quantity, status, created_at) VALUES (?, ?, ?, ?, ?)",
                orders,
                orders.size(),
                (ps, order) -> {
                    ps.setString(1, order.getOrderKey());
                    ps.setLong(2, order.getProductId());
                    ps.setInt(3, order.getQuantity());
                    ps.setString(4, order.getStatus().name());
                    ps.setTimestamp(5, Timestamp.valueOf(order.getCreatedAt()));
                });
    }
}
