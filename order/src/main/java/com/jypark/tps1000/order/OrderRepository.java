package com.jypark.tps1000.order;

import com.jypark.tps1000.order.dto.MonthlyProductSalesResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderKey(String orderKey);

    boolean existsByOrderKey(String orderKey);

    /** 배치 컨슈머의 기처리 필터: 배치당 SELECT 1회로 멱등 검사 (측정 1-c-ii) */
    @Query("select o.orderKey from Order o where o.orderKey in :orderKeys")
    List<String> findExistingOrderKeys(@Param("orderKeys") Collection<String> orderKeys);

    /**
     * 월별 상품 판매 집계 (내부 API용 — MSA 3b). GROUP BY는 DB가 수행하고 결과는 상품 수 규모.
     * FAILED 제외 기준은 정산 정책(decisions.md 14번)과 동일 — 호출 측이 아닌 데이터 소유자가 강제한다.
     * 정렬은 product_id 고정: 페이지 간 중복·누락 없는 안정적 페이징의 전제.
     */
    @Query("select new com.jypark.tps1000.order.dto.MonthlyProductSalesResponse(o.productId, sum(o.quantity)) " +
            "from Order o where o.createdAt >= :start and o.createdAt < :end and o.status <> :excluded " +
            "group by o.productId order by o.productId")
    List<MonthlyProductSalesResponse> aggregateMonthlySales(@Param("start") LocalDateTime start,
                                                            @Param("end") LocalDateTime end,
                                                            @Param("excluded") OrderStatus excluded,
                                                            Pageable pageable);
}
