package com.sparta.one_stop.global.config;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@ConditionalOnProperty(name = "app.test-data.init", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class TestDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${test.super-admin.email:superadmin@test.com}")
    private String superAdminEmail;

    @Value("${test.super-admin.password:Admin@1234!}")
    private String superAdminPassword;

    @Value("${test.buyer.email:buyer@test.com}")
    private String buyerEmail;

    @Value("${test.buyer.password:Buyer@1234!}")
    private String buyerPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail(superAdminEmail)) {
            userRepository.save(User.builder()
                .email(superAdminEmail)
                .password(passwordEncoder.encode(superAdminPassword))
                .name("슈퍼관리자")
                .role(UserRole.SUPER_ADMIN)
                .build());
        }
        if (!userRepository.existsByEmail(buyerEmail)) {
            userRepository.save(User.builder()
                .email(buyerEmail)
                .password(passwordEncoder.encode(buyerPassword))
                .name("일반회원")
                .role(UserRole.BUYER)
                .build());
        }
    }
}
