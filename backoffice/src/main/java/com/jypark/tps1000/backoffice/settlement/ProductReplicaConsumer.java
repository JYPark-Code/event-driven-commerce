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
 *
 * 복제본 재구축 = 새 컨슈머 그룹: auto-offset-reset=earliest라 그룹 이름을 바꾸면 토픽을
 * 처음부터 재생해 복제본이 통째로 재구축된다. DB 스키마 분리(3b-2) 때 실제로 이 방식으로
 * 새 스키마(tps_backoffice)에 복제본을 채웠다 — 토픽 보존 기간이 곧 재구축 가능 기간.
 *
 * 그룹 이름이 프로퍼티인 이유: 조합 하네스(app)와 분리 앱(backoffice-app)은 DB가 다르므로
 * 그룹을 공유하면 한쪽이 이벤트를 가져가 다른 쪽 복제본에 구멍이 난다(경쟁 소비) —
 * 3b-2 작업 중 실제로 겪은 문제. 배포 단위마다 자기 그룹 = 각자 토픽 전체를 독립 소비.
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
            groupId = "${backoffice.replica-consumer-group}",
            containerFactory = "productReplicaContainerFactory")
    @Transactional
    public void consume(String message) throws Exception {
        ProductChangedMessage changed = objectMapper.readValue(message, ProductChangedMessage.class);
        replicaRepository.save(ProductReplica.of(changed.productId(), changed.name(), changed.price()));
    }
}
