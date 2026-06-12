package com.jypark.tps1000.product;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 미존재 엔티티 조회를 404로 변환 (decisions.md 24번) — order 쪽 OrderExceptionHandler와
 * 같은 결함의 같은 수리. 모듈별 어드바이스 이유는 그쪽 주석 참고.
 */
@RestControllerAdvice
public class ProductExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
