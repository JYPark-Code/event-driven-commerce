package com.jypark.tps1000.backoffice.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * ADMIN 시드 계정 생성. 가입 API는 USER만 만들 수 있으므로(권한 상승 차단)
 * 관리자 1명은 부팅 시 시드한다. 운영이라면 환경변수 주입 + 최초 로그인 시 변경 강제가 정석.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminAccountInitializer(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${backoffice.admin.username}") String username,
                                   @Value("${backoffice.admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(User.create(username, passwordEncoder.encode(password), UserRole.ADMIN));
    }
}
