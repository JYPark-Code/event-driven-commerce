package com.jypark.tps1000;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jypark.tps1000.order.OrderNotificationService;
import com.jypark.tps1000.product.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 모든 통합 테스트의 공통 베이스. docker compose 인프라(MySQL 3307, Kafka 9092)가 떠 있어야 한다.
 *
 * 모든 테스트 클래스가 이 클래스만 상속하면 컨텍스트 캐시 키가 같아져 단일 컨텍스트를 공유한다.
 * 컨텍스트가 갈리면(예: 일부만 @MockitoSpyBean 보유) 캐시에 살아있는 여러 컨텍스트의
 * Kafka 컨슈머가 같은 그룹으로 경쟁 소비해서, 다른 컨텍스트가 이벤트를 가져간 테스트가
 * 간헐적으로 실패한다. → spy 선언을 여기 한 곳에 둔다.
 *
 * RANDOM_PORT: 정산 리더가 주문 집계를 HTTP 내부 API로 읽으므로(MSA 3b) 실제 서버가 떠 있어야
 * 한다. MockMvc는 RANDOM_PORT에서도 동작하므로 기존 테스트는 그대로. order-service URL은
 * 런타임에 배정된 포트로 치환 — 조합 앱에선 자기 자신이 order API를 서빙한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "backoffice.order-service.url=http://localhost:${local.server.port}")
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoSpyBean
    protected OrderNotificationService notificationService;

    /** 캐시 계층 테스트에서 DB 접근 횟수 검증용 (spy는 컨텍스트 분기 방지를 위해 여기에만 둔다). */
    @MockitoSpyBean
    protected ProductRepository productRepository;
}
