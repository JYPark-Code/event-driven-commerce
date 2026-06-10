package com.jypark.tps1000.backoffice;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** RBAC 동작 확인용 최소 관리자 엔드포인트. 정산 API(축 3-2)도 /api/admin 아래에 둔다. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Map.of("username", authentication.getName(), "roles", roles);
    }
}
