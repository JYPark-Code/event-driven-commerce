package com.jypark.tps1000.product;

import com.jypark.tps1000.IntegrationTestBase;
import com.jypark.tps1000.product.dto.CreateProductRequest;
import com.jypark.tps1000.product.dto.UpdateProductRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiTest extends IntegrationTestBase {

    @Test
    @DisplayName("상품 생성: 201 + 생성된 값이 조회로 다시 확인된다")
    void createProduct_thenGet() throws Exception {
        long productId = createProduct("키보드", 89000L);

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.name").value("키보드"))
                .andExpect(jsonPath("$.price").value(89000));
    }

    @Test
    @DisplayName("상품 수정: 200 + 다음 조회가 수정된 값을 반환한다")
    void updateProduct_thenGetReturnsNewValue() throws Exception {
        long productId = createProduct("마우스", 30000L);

        var update = new UpdateProductRequest("마우스(특가)", 25000L);
        mockMvc.perform(put("/api/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(25000));

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("마우스(특가)"))
                .andExpect(jsonPath("$.price").value(25000));
    }

    @Test
    @DisplayName("상품 생성 검증: 이름 누락이면 400")
    void createProduct_blankName_badRequest() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(" ", 1000L))))
                .andExpect(status().isBadRequest());
    }

    protected long createProduct(String name, long price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(name, price))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").isNumber())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("productId").asLong();
    }
}
