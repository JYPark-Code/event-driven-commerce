package com.jypark.tps1000.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 경로 기반 라우팅 (MSA 3c — docs/msa-architecture.md).
 * Java DSL인 이유: yml 라우트는 Spring Cloud 버전에 따라 프로퍼티 prefix가 바뀌어 왔지만
 * RouteLocatorBuilder API는 안정적이고, 라우팅 규칙이 코드 리뷰 대상이 된다.
 *
 * /internal/** 는 의도적으로 라우팅하지 않는다 — 서비스 간 내부 API(주문 집계 등)는
 * 게이트웨이(외부 진입점)에서 404. 로컬 데모는 서비스 포트(8081~8083)가 호스트에 직접
 * 열려 있어 우회가 가능했는데, 지금은 우회해도 공유 시크릿 검증(InternalApiTokenFilter,
 * decisions.md 23번)에 걸린다. 운영이면 내부망 바인딩이 추가될 지점.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator serviceRoutes(RouteLocatorBuilder builder,
                                      @Value("${gateway.product-url}") String productUrl,
                                      @Value("${gateway.order-url}") String orderUrl,
                                      @Value("${gateway.backoffice-url}") String backofficeUrl) {
        return builder.routes()
                .route("product", r -> r.path("/api/products/**").uri(productUrl))
                .route("order", r -> r.path("/api/orders/**").uri(orderUrl))
                .route("backoffice", r -> r.path("/api/auth/**", "/api/admin/**").uri(backofficeUrl))
                .build();
    }
}
