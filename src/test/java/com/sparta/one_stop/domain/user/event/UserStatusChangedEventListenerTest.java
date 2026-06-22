package com.sparta.one_stop.domain.user.event;

import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserStatusChangedEventListenerTest {

    @Test
    void committed_status_change_evicts_cached_status() {
        UserStatusCacheService cache = mock(UserStatusCacheService.class);

        new UserStatusChangedEventListener(cache).handle(new UserStatusChangedEvent(2L));

        verify(cache).evict(2L);
    }
}
