package com.jypark.tps1000.backoffice.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findBySettlementMonthOrderByProductId(String settlementMonth);

    long countBySettlementMonth(String settlementMonth);

    void deleteBySettlementMonth(String settlementMonth);
}
