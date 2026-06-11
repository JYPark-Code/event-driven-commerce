package com.jypark.tps1000.product.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jypark.tps1000.product.dto.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 상품 조회 캐시 계층: L1(Caffeine, 프로세스 내) → L2(Redis, 공유) → MySQL.
 *
 * 읽기(look-aside): L1 미스 → L2 조회 → DB 로드 후 L2/L1 역방향 적재.
 *  - Cache Stampede 대응: Caffeine get(key, loader)가 같은 키의 동시 미스를 키 단위로 직렬화해
 *    인스턴스 내에서는 DB 로드가 1회만 실행된다(single-flight). 인스턴스 간 중복 로드는 허용
 *    (인스턴스 수 = 최대 중복 횟수라 폭주가 아님 — 분산락/PER 대안은 docs/decisions.md).
 *  - L2 TTL에 jitter를 더해 핫키들이 같은 순간에 일제히 만료되는 것을 분산.
 *
 * 쓰기 무효화: L2 삭제 + pub/sub로 모든 인스턴스의 L1 evict (호출 측이 DB 커밋 후에 호출해야 함).
 *  - pub/sub는 at-most-once라 유실 가능 → L1 TTL 60초가 불일치 상한(백스톱).
 *
 * Redis 장애는 캐시 미스로 강등한다 — 캐시 계층 장애가 조회 실패로 전파되지 않게.
 */
@Slf4j
@Component
public class ProductCacheLayer implements MessageListener {

    public static final String INVALIDATION_CHANNEL = "cache:invalidate:product";

    private static final long L1_MAX_SIZE = 10_000;
    private static final Duration L1_TTL = Duration.ofSeconds(60);
    private static final Duration L2_TTL = Duration.ofMinutes(10);
    private static final long L2_TTL_JITTER_MAX_SECONDS = 60;
    private static final String L2_KEY_PREFIX = "product:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Cache<Long, ProductResponse> l1;

    public ProductCacheLayer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.l1 = Caffeine.newBuilder()
                .maximumSize(L1_MAX_SIZE)
                .expireAfterWrite(L1_TTL)
                .build();
    }

    public ProductResponse get(Long productId, Function<Long, ProductResponse> dbLoader) {
        return l1.get(productId, id -> loadFromL2OrDb(id, dbLoader));
    }

    /** 무효화. 반드시 DB 커밋 후 호출 — 커밋 전이면 다른 스레드가 커밋 전 옛값을 다시 캐시할 수 있다. */
    public void evict(Long productId) {
        try {
            redisTemplate.delete(L2_KEY_PREFIX + productId);
            redisTemplate.convertAndSend(INVALIDATION_CHANNEL, productId.toString());
        } catch (DataAccessException e) {
            // L2에 옛값이 남으면 L2 TTL(10분)까지 불일치 가능 — 데모 범위에서 수용 (decisions.md)
            log.error("redis evict failed: productId={}", productId, e);
        }
        l1.invalidate(productId); // 자기 인스턴스는 pub/sub 왕복을 기다리지 않고 즉시 제거
    }

    /** pub/sub 수신: 다른 인스턴스(또는 자신)의 쓰기 → 로컬 L1 제거. */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            l1.invalidate(Long.valueOf(body));
        } catch (NumberFormatException e) {
            log.warn("invalid invalidation message: {}", body);
        }
    }

    /** 검증용(테스트·측정) — L1에 적재돼 있으면 반환, 없으면 null. 조회 경로에서는 쓰지 말 것. */
    public ProductResponse peekL1(Long productId) {
        return l1.getIfPresent(productId);
    }

    private ProductResponse loadFromL2OrDb(Long productId, Function<Long, ProductResponse> dbLoader) {
        ProductResponse fromL2 = getFromL2(productId);
        if (fromL2 != null) {
            return fromL2;
        }
        ProductResponse loaded = dbLoader.apply(productId);
        putToL2(productId, loaded);
        return loaded;
    }

    private ProductResponse getFromL2(Long productId) {
        try {
            String json = redisTemplate.opsForValue().get(L2_KEY_PREFIX + productId);
            return json == null ? null : objectMapper.readValue(json, ProductResponse.class);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("L2 read failed, fallback to DB: productId={}", productId, e);
            return null;
        }
    }

    private void putToL2(Long productId, ProductResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            Duration ttl = L2_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(L2_TTL_JITTER_MAX_SECONDS + 1));
            redisTemplate.opsForValue().set(L2_KEY_PREFIX + productId, json, ttl);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("L2 write skipped: productId={}", productId, e);
        }
    }
}
