package com.jypark.tps1000.backoffice.auth;

import com.jypark.tps1000.backoffice.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 발급/검증 (HS256 대칭키). 단일 서버 데모라 대칭키로 충분 —
 * 발급자와 검증자가 분리되면 RS256(공개키 검증)으로 전환할 지점 (docs/decisions.md 13번).
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirySeconds;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiry-seconds}") long expirySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirySeconds = expirySeconds;
    }

    /**
     * 역할을 클레임에 넣어 요청마다 DB 조회 없이 인가한다.
     * 트레이드오프: 역할 변경이 토큰 만료(1시간)까지 반영 안 됨 — 데모에서 수용.
     */
    public String issue(String username, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 포함 파싱. 위조/만료 시 JwtException. */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
        return new ParsedToken(claims.getSubject(), UserRole.valueOf(claims.get("role", String.class)));
    }

    public long expirySeconds() {
        return expirySeconds;
    }

    public record ParsedToken(String username, UserRole role) {
    }
}
