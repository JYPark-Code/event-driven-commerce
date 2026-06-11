package com.jypark.tps1000.backoffice.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * product.changed 이벤트로 상품 복제본을 유지한다 (MSA 2단계).
 *
 * 순서·멱등: 키 = productId라 같은 상품의 이벤트는 같은 파티션에서 순차 처리되고,
 * PK upsert라 at-least-once 재전달에도 결과가 같다.
 *
 * 실패 처리: 별도 DLQ 없음 — 기본 에러 핸들러가 재시도 후 스킵한다. 복제본은 다음 변경
 * 이벤트가 자기 치유하는 데이터라, 주문(9번)처럼 건별 보존이 필요하지 않다.
 * 컨슈머 그룹을 분리(auto-offset-reset=earliest)해 새로 배포돼도 토픽 처음부터 복제한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductReplicaConsumer {

    private final ProductReplicaRepository replicaRepository;
    private final ObjectMapper objectMapper;

    /** 컨슈머가 소유한 계약 표현 — product 모듈의 이벤트 클래스를 공유하지 않는다. 미지 필드는 무시(Boot 기본). */
    record ProductChangedMessage(Long productId, String name, long price) {
    }

    @KafkaListener(topics = ProductReplicaKafkaConfig.PRODUCT_CHANGED_TOPIC,
            groupId = "tps1000-product-replica",
            containerFactory = "productReplicaContainerFactory")
    @Transactional
    public void consume(String message) throws Exception {
        ProductChangedMessage changed = objectMapper.readValue(message, ProductChangedMessage.class);
        replicaRepository.save(ProductReplica.of(changed.productId(), changed.name(), changed.price()));
    }
}
