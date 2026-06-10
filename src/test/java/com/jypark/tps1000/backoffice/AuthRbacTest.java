package com.jypark.tps1000.backoffice;

import com.fasterxml.jackson.databind.JsonNode;
import com.jypark.tps1000.IntegrationTestBase;
import com.jypark.tps1000.backoffice.auth.dto.LoginRequest;
import com.jypark.tps1000.backoffice.auth.dto.SignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 축 3 RBAC 검증: 가입/로그인 → 토큰 발급, /api/admin/** 보호(401/403/200),
 * 부하테스트 대상 공개 경로가 영향받지 않는지까지 확인한다.
 * DB가 테스트 간 보존되므로(ddl-auto: update) username은 매번 UUID로 생성한다.
 */
class AuthRbacTest extends IntegrationTestBase {

    private static final String PASSWORD = "password123!";

    @Test
    @DisplayName("가입 후 로그인하면 Bearer 토큰이 발급된다")
    void signup_thenLogin_issuesToken() throws Exception {
        String username = newUsername();
        signup(username);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("같은 username으로 다시 가입하면 409")
    void signup_duplicateUsername_conflict() throws Exception {
        String username = newUsername();
        signup(username);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(username, PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 (미존재 계정과 같은 응답)")
    void login_wrongPassword_unauthorized() throws Exception {
        String username = newUsername();
        signup(username);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 API: 토큰 없으면 401")
    void adminApi_withoutToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 API: USER 토큰이면 403 (인증은 됐지만 권한 부족)")
    void adminApi_withUserToken_forbidden() throws Exception {
        String username = newUsername();
        signup(username);
        String userToken = login(username, PASSWORD);

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 API: ADMIN(시드 계정) 토큰이면 200")
    void adminApi_withAdminToken_ok() throws Exception {
        String adminToken = login("admin", "admin1234!");

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("위조 토큰이면 인증 미설정으로 401")
    void adminApi_withInvalidToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("부하테스트 대상 공개 경로(상품 조회)는 RBAC 적용 후에도 토큰 없이 접근된다")
    void publicApi_withoutToken_stillOpen() throws Exception {
        // 존재하지 않는 ID라도 보안 계층을 통과해 도메인 응답(404 아님 401/403)이 나오면 충분 — 200/404 둘 다 허용
        int status = mockMvc.perform(get("/api/products/1"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
    }

    private String newUsername() {
        return "user-" + UUID.randomUUID().toString().substring(0, 18);
    }

    private void signup(String username) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(username, PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
