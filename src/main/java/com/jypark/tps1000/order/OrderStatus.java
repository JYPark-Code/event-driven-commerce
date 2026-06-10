package com.jypark.tps1000.order;

public enum OrderStatus {
    CREATED,    // 접수 완료 (동기: 저장 즉시 / 비동기: Kafka 발행 후 컨슈머가 확정)
    COMPLETED,  // 후속 처리(알림 등)까지 완료
    FAILED      // 처리 실패 (비동기 버전에서 DLQ 라우팅 대상)
}
