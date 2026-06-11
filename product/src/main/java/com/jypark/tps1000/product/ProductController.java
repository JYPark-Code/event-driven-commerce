package com.jypark.tps1000.product;

import com.jypark.tps1000.product.dto.CreateProductRequest;
import com.jypark.tps1000.product.dto.ProductResponse;
import com.jypark.tps1000.product.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productService.createProduct(request);
    }

    /** 축 2 측정 대상 엔드포인트 — 캐시 계층(L1/L2) 유무에 따른 TPS·p95 비교. */
    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable Long productId) {
        return productService.getProduct(productId);
    }

    /** 수정은 캐시 무효화 경로 검증용 (백오피스 기능은 축 3에서). */
    @PutMapping("/{productId}")
    public ProductResponse updateProduct(@PathVariable Long productId,
                                         @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(productId, request);
    }
}
