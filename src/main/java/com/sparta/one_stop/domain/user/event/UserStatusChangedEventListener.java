package com.sparta.one_stop.domain.user.event;

import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserStatusChangedEventListener {

    private final UserStatusCacheService userStatusCacheService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserStatusChangedEvent event) {
        userStatusCacheService.evict(event.userId());
    }
}
