package com.jypark.tps1000.order;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 미존재 엔티티 조회를 404로 변환 (decisions.md 24번). 핸들러가 없으면
 * EntityNotFoundException이 500으로 새서 클라이언트가 "없음"과 "서버 장애"를 구분할 수
 * 없고, 미존재 ID 스캔이 서버 에러로 기록돼 모니터링 노이즈가 된다.
 * 어드바이스도 모듈별로 둔다(17번 경계 원칙) — 조합 앱에선 product 쪽과 공존하지만
 * 같은 예외를 같은 404로 매핑하므로 어느 쪽이 잡아도 결과가 같다.
 */
@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
