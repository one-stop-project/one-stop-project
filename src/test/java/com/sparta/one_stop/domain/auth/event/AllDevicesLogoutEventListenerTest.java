package com.sparta.one_stop.domain.auth.event;

import com.sparta.one_stop.domain.auth.service.DeviceLimitService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllDevicesLogoutEventListenerTest {

    @Mock DeviceLimitService deviceLimitService;
    @Mock RedisTokenService redisTokenService;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserStatusCacheService userStatusCacheService;

    @Test
    void password_change_evicts_version_then_blocks_old_access_tokens_and_removes_sessions() {
        long userId = 1L;
        when(jwtTokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);
        when(deviceLimitService.removeAllDevices(userId)).thenReturn(2L);

        AllDevicesLogoutEventListener listener = new AllDevicesLogoutEventListener(
            deviceLimitService, redisTokenService, jwtTokenProvider, userStatusCacheService);

        listener.handle(new AllDevicesLogoutEvent(userId, "PASSWORD_CHANGED"));

        InOrder order = inOrder(userStatusCacheService, redisTokenService, deviceLimitService);
        order.verify(userStatusCacheService).evictTokenVersion(userId);
        order.verify(redisTokenService).invalidateUserTokens(userId, 900L);
        order.verify(deviceLimitService).removeAllDevices(userId);
    }

    @Test
    void cleanup_continues_when_token_version_cache_eviction_fails() {
        long userId = 1L;
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
            .when(userStatusCacheService).evictTokenVersion(userId);
        when(jwtTokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);

        AllDevicesLogoutEventListener listener = new AllDevicesLogoutEventListener(
            deviceLimitService, redisTokenService, jwtTokenProvider, userStatusCacheService);

        listener.handle(new AllDevicesLogoutEvent(userId, "PASSWORD_CHANGED"));

        verify(redisTokenService).invalidateUserTokens(userId, 900L);
        verify(deviceLimitService).removeAllDevices(userId);
    }
}
