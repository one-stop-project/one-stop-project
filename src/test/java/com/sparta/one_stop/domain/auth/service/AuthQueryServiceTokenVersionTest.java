package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import com.sparta.one_stop.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthQueryServiceTokenVersionTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserStatusCacheService userStatusCacheService;

    @Test
    void access_token_issued_before_password_change_is_rejected() {
        AuthQueryService service = new AuthQueryService(userRepository, passwordEncoder, userStatusCacheService);
        when(userStatusCacheService.getTokenVersion(1L)).thenReturn(2);

        assertThatThrownBy(() -> service.verifyTokenVersion(1L, 1))
            .isInstanceOf(CustomException.class);
    }
}
