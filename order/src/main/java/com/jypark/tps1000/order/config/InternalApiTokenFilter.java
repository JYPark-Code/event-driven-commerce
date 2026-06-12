package com.jypark.tps1000.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 서비스 간 내부 API(/internal/**) 보호 — 공유 시크릿 헤더 검증 (보안 하드닝, decisions.md 23번).
 * 게이트웨이는 /internal/**를 라우팅하지 않지만(22번) 로컬 데모는 서비스 포트가 호스트에
 * 직접 열려 있어 우회 호출이 가능했던 지점. 호출자는 정산 리더(backoffice)뿐이다.
 * 운영이면 내부망 격리/mTLS로 올라갈 지점 — 공유 시크릿은 그 전 단계의 최소 방어.
 *
 * order 모듈에 두는 이유: spring-security 의존이 없는 order-app에서도 동작해야 한다.
 * 헤더 이름은 backoffice와 문자열로만 공유 — 상수 클래스 공유도 컴파일 의존이라 두지 않는다(18번 원칙).
 */
@Component
public class InternalApiTokenFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Internal-Token";

    private final byte[] expectedToken;

    public InternalApiTokenFilter(@Value("${internal.api-token}") String token) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(TOKEN_HEADER);
        // MessageDigest.isEqual = 상수 시간 비교 — equals는 불일치 위치에 따라 시간이 달라져
        // 타이밍 공격으로 토큰을 앞에서부터 맞출 수 있다.
        if (provided == null || !MessageDigest.isEqual(expectedToken, provided.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
