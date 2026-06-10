package com.jypark.tps1000.product;

import com.jypark.tps1000.product.dto.CreateProductRequest;
import com.jypark.tps1000.product.dto.ProductResponse;
import com.jypark.tps1000.product.dto.UpdateProductRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = productRepository.save(Product.create(request.name(), request.price()));
        return ProductResponse.from(product);
    }

    /** 캐시 없는 베이스라인 조회. 축 2에서 L1/L2 계층을 얹어 비교 측정한다. */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("product not found: " + productId));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("product not found: " + productId));
        product.update(request.name(), request.price());
        return ProductResponse.from(product);
    }
}
