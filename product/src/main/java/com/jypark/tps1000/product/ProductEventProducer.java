package com.jypark.tps1000.product;

import com.jypark.tps1000.product.config.ProductKafkaConfig;
import com.jypark.tps1000.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventProducer {

    private final KafkaTemplate<String, ProductChangedEvent> kafkaTemplate;

    /**
     * 호출 측이 DB 커밋 후에 호출해야 한다 — 커밋 전 발행이면 롤백된 상품이 복제될 수 있다.
     * 발행 실패 시 복제본(정산용)이 옛값으로 남는다: 콜백 로그로 감지, 다음 변경 이벤트가 자기 치유.
     * 운영이면 outbox로 보완할 지점 (주문 발행과 같은 트레이드오프 — decisions.md 7번).
     */
    public void publishChanged(ProductChangedEvent event) {
        kafkaTemplate.send(ProductKafkaConfig.PRODUCT_CHANGED_TOPIC, event.productId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("상품 변경 이벤트 발행 실패 productId={}", event.productId(), ex);
                    }
                });
    }
}
