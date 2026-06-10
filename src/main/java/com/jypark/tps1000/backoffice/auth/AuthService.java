package com.jypark.tps1000.backoffice.auth;

import com.jypark.tps1000.backoffice.auth.dto.LoginRequest;
import com.jypark.tps1000.backoffice.auth.dto.SignupRequest;
import com.jypark.tps1000.backoffice.auth.dto.SignupResponse;
import com.jypark.tps1000.backoffice.auth.dto.TokenResponse;
import com.jypark.tps1000.backoffice.user.User;
import com.jypark.tps1000.backoffice.user.UserRepository;
import com.jypark.tps1000.backoffice.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /** 가입은 항상 USER — API로 ADMIN을 만들 수 없게 권한 상승 경로를 차단. ADMIN은 부팅 시드뿐. */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
        }
        User user = User.create(request.username(), passwordEncoder.encode(request.password()), UserRole.USER);
        userRepository.save(user);
        return SignupResponse.from(user);
    }

    /** 미존재 계정과 비밀번호 불일치를 같은 401로 — 계정 존재 여부 노출(user enumeration) 방지. */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        return new TokenResponse(tokenProvider.issue(user.getUsername(), user.getRole()),
                "Bearer", tokenProvider.expirySeconds());
    }
}
