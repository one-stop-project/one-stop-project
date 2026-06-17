package com.sparta.one_stop.domain.product.scheduler;

import static org.mockito.BDDMockito.then;

import com.sparta.one_stop.domain.product.service.ProductViewCountService;
import com.sparta.one_stop.domain.product.service.ProductViewCountSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductViewCountScheduler - 주간 리셋")
class ProductViewCountSchedulerTest {

    @Mock
    private ProductViewCountService viewCountService;

    @Mock
    private ProductViewCountSyncService syncService;

    @InjectMocks
    private ProductViewCountScheduler scheduler;

    @Test
    @DisplayName("weeklyReset은 Redis 잔여 카운터를 먼저 정리한 뒤 DB를 초기화한다")
    void weeklyReset_clearsRedisThenResetsDb() {
        // when
        scheduler.weeklyReset();

        // then — 동시에 도는 sync가 잔여 카운트를 DB로 되돌리는 창을 줄이려면 Redis 정리가 DB 초기화보다 먼저여야 한다
        InOrder inOrder = Mockito.inOrder(viewCountService, syncService);
        inOrder.verify(viewCountService).clearAllCounters();
        inOrder.verify(syncService).resetAllViewCounts();
    }
}
