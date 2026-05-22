package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2MemberService {

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreateUser(OAuth2UserInfo userInfo) {
        Optional<User> existing = userRepository.findByProviderAndProviderId(
            userInfo.getProvider(), userInfo.getProviderId()
        );

        if (existing.isPresent()) {
            User user = existing.get();
            user.verifyActive();
            user.recordLogin();
            return user;
        }

        if (userRepository.existsByEmail(userInfo.getEmail())) {
            throw new CustomException(ErrorCode.AUTH_019);
        }

        // 프록시 외부 호출 구조 형성 -> REQUIRES_NEW 독립 트랜잭션 100% 보장
        return createNewOAuth2User(userInfo);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createNewOAuth2User(OAuth2UserInfo userInfo) {
        User newUser = User.builder()
            .email(userInfo.getEmail())
            .password("{noop}" + UUID.randomUUID())
            .name(userInfo.getName() != null ? userInfo.getName() : "소셜회원")
            .role(UserRole.BUYER)
            .build();

        newUser.linkOAuth2(userInfo.getProvider(), userInfo.getProviderId());

        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            return userRepository.findByProviderAndProviderId(userInfo.getProvider(), userInfo.getProviderId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_018));
        }
    }
}
