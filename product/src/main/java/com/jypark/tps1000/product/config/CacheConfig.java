package com.jypark.tps1000.product.config;

import com.jypark.tps1000.product.cache.ProductCacheLayer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 캐시 무효화 pub/sub 배선. 멀티 인스턴스에서 한 인스턴스의 쓰기가
 * 다른 인스턴스의 L1(프로세스 내 Caffeine)을 무효화하는 유일한 경로다.
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       ProductCacheLayer productCacheLayer) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(productCacheLayer, new ChannelTopic(ProductCacheLayer.INVALIDATION_CHANNEL));
        return container;
    }
}
