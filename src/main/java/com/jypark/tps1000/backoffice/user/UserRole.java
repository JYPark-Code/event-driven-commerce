package com.jypark.tps1000.backoffice.user;

/**
 * 백오피스 역할. 역할 2개뿐이라 enum 단일 컬럼 — Role/Permission 테이블 분리는
 * 데모 범위에선 과한 선택 (근거: docs/decisions.md 13번).
 */
public enum UserRole {
    USER, ADMIN;

    /** Spring Security 권한 문자열. hasRole("ADMIN")은 ROLE_ 접두사를 붙여 비교한다. */
    public String authority() {
        return "ROLE_" + name();
    }
}
