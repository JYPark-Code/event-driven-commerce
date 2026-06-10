package com.jypark.tps1000;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jypark.tps1000.order.OrderNotificationService;
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
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoSpyBean
    protected OrderNotificationService notificationService;
}
