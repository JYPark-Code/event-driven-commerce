package com.jypark.tps1000.product.event;

/**
 * 상품 생성·수정 시 발행되는 변경 이벤트 (MSA 2단계 — 정산의 상품 데이터 복제용).
 * 컨슈머(backoffice)는 이 클래스를 공유하지 않고 JSON 스키마로만 계약한다 —
 * 필드 추가는 호환, 필드 이름 변경·삭제는 컨슈머와 함께 바꿔야 하는 파괴적 변경.
 */
public record ProductChangedEvent(
        Long productId,
        String name,
        long price
) {
}
