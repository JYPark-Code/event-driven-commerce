package com.jypark.tps1000.product;

import com.jypark.tps1000.product.cache.ProductCacheLayer;
import com.jypark.tps1000.product.dto.CreateProductRequest;
import com.jypark.tps1000.product.dto.ProductResponse;
import com.jypark.tps1000.product.dto.UpdateProductRequest;
import com.jypark.tps1000.product.event.ProductChangedEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCacheLayer cacheLayer;
    private final ProductEventProducer eventProducer;
    private final TransactionTemplate transactionTemplate;

    /**
     * 생성 시 캐시 적재는 안 한다(look-aside) — 첫 조회가 적재한다.
     * 변경 이벤트는 커밋 후 발행 (MSA 2단계 — 정산의 상품 복제본이 이 이벤트로 채워진다).
     */
    public ProductResponse createProduct(CreateProductRequest request) {
        ProductResponse response = transactionTemplate.execute(status ->
                ProductResponse.from(productRepository.save(Product.create(request.name(), request.price()))));
        eventProducer.publishChanged(new ProductChangedEvent(response.productId(), response.name(), response.price()));
        return response;
    }

    /** 축 2 측정 대상: L1(Caffeine) → L2(Redis) → MySQL. 캐시 히트 시 트랜잭션·커넥션을 아예 안 탄다. */
    public ProductResponse getProduct(Long productId) {
        return cacheLayer.get(productId, this::loadFromDb);
    }

    /**
     * 수정 트랜잭션을 TransactionTemplate으로 감싸 커밋 시점을 코드에 드러내고,
     * 무효화는 커밋 "후"에 수행한다. @Transactional 메서드 안에서 evict하면
     * 커밋 전에 다른 스레드가 옛값을 읽어 캐시를 다시 채울 수 있다(write 유실처럼 보이는 불일치).
     */
    public ProductResponse updateProduct(Long productId, UpdateProductRequest request) {
        ProductResponse response = transactionTemplate.execute(status -> {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new EntityNotFoundException("product not found: " + productId));
            product.update(request.name(), request.price());
            return ProductResponse.from(product);
        });
        cacheLayer.evict(productId);
        eventProducer.publishChanged(new ProductChangedEvent(response.productId(), response.name(), response.price()));
        return response;
    }

    private ProductResponse loadFromDb(Long productId) {
        return productRepository.findById(productId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("product not found: " + productId));
    }
}
