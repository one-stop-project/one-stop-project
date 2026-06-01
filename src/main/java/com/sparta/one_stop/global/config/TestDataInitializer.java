package com.sparta.one_stop.global.config;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@RequiredArgsConstructor
public class TestDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail("superadmin@test.com")) {
            userRepository.save(User.builder()
                .email("superadmin@test.com")
                .password(passwordEncoder.encode("Admin@1234!"))
                .name("슈퍼관리자")
                .role(UserRole.SUPER_ADMIN)
                .build());
        }
        if (!userRepository.existsByEmail("buyer@test.com")) {
            userRepository.save(User.builder()
                .email("buyer@test.com")
                .password(passwordEncoder.encode("Buyer@1234!"))
                .name("일반회원")
                .role(UserRole.BUYER)
                .build());
        }
    }
}
