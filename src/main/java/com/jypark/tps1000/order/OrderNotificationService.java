package com.jypark.tps1000.order;

import com.jypark.tps1000.order.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 주문 접수 후속 작업(알림) 데모. 동기 버전엔 없는 "컨슈머가 떠안는 일"을 대표한다.
 * 별도 빈으로 둔 이유: 테스트에서 실패를 주입해 재시도/DLQ 경로를 검증하는 seam.
 */
@Slf4j
@Service
public class OrderNotificationService {

    public void notifyOrderAccepted(OrderCreatedEvent event) {
        log.info("주문 접수 알림 전송 orderKey={} productId={}", event.orderKey(), event.productId());
    }
}
