package com.jypark.tps1000.backoffice.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 백오피스 사용자 (축 3). USER는 SQL 예약어라 테이블명은 users. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 해시(60자 고정). 평문은 저장하지 않는다. */
    @Column(nullable = false, length = 60)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private User(String username, String encodedPassword, UserRole role) {
        this.username = username;
        this.password = encodedPassword;
        this.role = role;
        this.createdAt = LocalDateTime.now();
    }

    /** encodedPassword는 반드시 인코딩된 값 — 인코딩 책임은 AuthService에 있다. */
    public static User create(String username, String encodedPassword, UserRole role) {
        return new User(username, encodedPassword, role);
    }
}
