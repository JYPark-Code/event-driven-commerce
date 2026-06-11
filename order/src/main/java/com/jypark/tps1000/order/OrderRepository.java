package com.jypark.tps1000.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderKey(String orderKey);

    boolean existsByOrderKey(String orderKey);

    /** 배치 컨슈머의 기처리 필터: 배치당 SELECT 1회로 멱등 검사 (측정 1-c-ii) */
    @Query("select o.orderKey from Order o where o.orderKey in :orderKeys")
    List<String> findExistingOrderKeys(@Param("orderKeys") Collection<String> orderKeys);
}
