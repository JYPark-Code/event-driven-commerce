package com.jypark.tps1000.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

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
                                      @Value("${gateway.backoffice-url}") String backofficeUrl,
                                      RedisRateLimiter authRateLimiter,
                                      KeyResolver ipKeyResolver) {
        return builder.routes()
                .route("product", r -> r.path("/api/products/**").uri(productUrl))
                .route("order", r -> r.path("/api/orders/**").uri(orderUrl))
                // 인증 경로만 IP별 rate limit (decisions.md 24번) — 로그인 브루트포스 방어.
                // 부하테스트 대상 경로(order·product)에 걸면 측정 조건이 바뀌어 인증 라우트만 분리.
                .route("auth", r -> r.path("/api/auth/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(authRateLimiter)
                                .setKeyResolver(ipKeyResolver)))
                        .uri(backofficeUrl))
                .route("backoffice", r -> r.path("/api/admin/**").uri(backofficeUrl))
                .build();
    }

    /**
     * 토큰 버킷 (Redis 공유 — 게이트웨이를 수평 확장해도 카운터가 하나).
     * 초과 시 429, 잔여량은 X-RateLimit-* 헤더로 노출. Redis 장애 시엔 통과(fail-open) —
     * 로그인 가용성을 인프라 장애에 묶지 않는 트레이드오프.
     */
    @Bean
    public RedisRateLimiter authRateLimiter(@Value("${gateway.auth-rate-limit.replenish-rate}") int replenishRate,
                                            @Value("${gateway.auth-rate-limit.burst-capacity}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }

    /** 버킷 키 = 클라이언트 IP. X-Forwarded-For는 위조 가능해 쓰지 않는다 — 직접 연결 주소 기준. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(InetSocketAddress::getAddress)
                        .map(InetAddress::getHostAddress)
                        .orElse("unknown"));
    }
}
