package com.jypark.tps1000.product.cache;

import com.jypark.tps1000.IntegrationTestBase;
import com.jypark.tps1000.product.ProductService;
import com.jypark.tps1000.product.dto.CreateProductRequest;
import com.jypark.tps1000.product.dto.ProductResponse;
import com.jypark.tps1000.product.dto.UpdateProductRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 캐시 계층(L1 Caffeine → L2 Redis → MySQL) 동작 검증.
 * DB 접근 횟수는 IntegrationTestBase의 ProductRepository spy로 센다.
 * 테스트마다 상품을 새로 만들므로(고유 id) 테스트 간 캐시 오염이 없다.
 */
class ProductCacheTest extends IntegrationTestBase {

    private static final String L2_KEY_PREFIX = "product:v1:";

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductCacheLayer cacheLayer;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("첫 조회는 DB 1회 + L1/L2 적재, 두 번째 조회는 DB를 안 탄다")
    void firstReadLoadsDb_secondReadHitsL1() {
        Long id = productService.createProduct(new CreateProductRequest("캐시-적재", 1000L)).productId();
        clearInvocations(productRepository);

        productService.getProduct(id);

        verify(productRepository, times(1)).findById(id);
        assertThat(cacheLayer.peekL1(id)).isNotNull();
        assertThat(redisTemplate.hasKey(L2_KEY_PREFIX + id)).isTrue();

        clearInvocations(productRepository);
        ProductResponse second = productService.getProduct(id);

        verify(productRepository, never()).findById(any());
        assertThat(second.name()).isEqualTo("캐시-적재");
    }

    @Test
    @DisplayName("pub/sub 무효화 메시지로 L1만 비워지면, 다음 조회는 L2(Redis)에서 채워진다 — DB 0회")
    void invalidationMessage_evictsL1_thenL2Serves() {
        Long id = productService.createProduct(new CreateProductRequest("L2-히트", 2000L)).productId();
        productService.getProduct(id); // L1/L2 적재
        assertThat(cacheLayer.peekL1(id)).isNotNull();

        // 다른 인스턴스의 쓰기를 흉내: 채널에 직접 publish → 구독 경로로 L1 evict (비동기라 await)
        redisTemplate.convertAndSend(ProductCacheLayer.INVALIDATION_CHANNEL, id.toString());
        await().atMost(Duration.ofSeconds(5)).until(() -> cacheLayer.peekL1(id) == null);

        clearInvocations(productRepository);
        ProductResponse response = productService.getProduct(id);

        verify(productRepository, never()).findById(any()); // L2 히트 — DB 안 탐
        assertThat(response.name()).isEqualTo("L2-히트");
        assertThat(cacheLayer.peekL1(id)).isNotNull(); // L1 재적재 확인
    }

    @Test
    @DisplayName("수정하면 L1/L2가 모두 무효화되고, 다음 조회는 DB에서 새 값을 읽는다")
    void update_evictsBothTiers_nextReadSeesNewValue() {
        Long id = productService.createProduct(new CreateProductRequest("무효화-전", 3000L)).productId();
        productService.getProduct(id); // L1/L2 적재

        productService.updateProduct(id, new UpdateProductRequest("무효화-후", 3500L));

        assertThat(cacheLayer.peekL1(id)).isNull();
        assertThat(redisTemplate.hasKey(L2_KEY_PREFIX + id)).isFalse();

        clearInvocations(productRepository);
        ProductResponse after = productService.getProduct(id);

        verify(productRepository, times(1)).findById(id);
        assertThat(after.name()).isEqualTo("무효화-후");
        assertThat(after.price()).isEqualTo(3500L);
    }

    @Test
    @DisplayName("Cache Stampede: 같은 키 동시 조회 20개 → DB 로드는 1회 (Caffeine single-flight)")
    void concurrentMisses_loadDbOnlyOnce() throws Exception {
        Long id = productService.createProduct(new CreateProductRequest("스탬피드", 4000L)).productId();
        clearInvocations(productRepository);

        int threads = 20;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<ProductResponse>> tasks = IntStream.range(0, threads)
                    .<Callable<ProductResponse>>mapToObj(i -> () -> {
                        barrier.await(); // 전원 동시에 미스 상태로 진입
                        return productService.getProduct(id);
                    })
                    .collect(Collectors.toList());

            List<Future<ProductResponse>> results = pool.invokeAll(tasks);
            for (Future<ProductResponse> future : results) {
                assertThat(future.get().name()).isEqualTo("스탬피드");
            }
        } finally {
            pool.shutdown();
        }

        verify(productRepository, times(1)).findById(id);
    }
}
