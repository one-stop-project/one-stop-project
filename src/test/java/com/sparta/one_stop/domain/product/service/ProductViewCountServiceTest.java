package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductViewCountService - Redis 집계 키 관리")
class ProductViewCountServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private ProductViewCountService viewCountService;

    @Nested
    @DisplayName("clearAllCounters - 주간 리셋용 잔여 카운터 정리")
    class ClearAllCounters {

        @Test
        @DisplayName("dirty 셋 키와 카운터 키 prefix를 인자로 스크립트를 실행한다")
        void executesScriptWithDirtyKeyAndCounterPrefix() {
            // given
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(3L);

            // when
            viewCountService.clearAllCounters();

            // then
            ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
            verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), argsCaptor.capture());

            assertThat(keysCaptor.getValue()).containsExactly("viewcount:dirty");
            assertThat(argsCaptor.getAllValues()).containsExactly("viewcount:product:");
        }

        @Test
        @DisplayName("스크립트가 정리한 상품 수를 그대로 반환한다")
        void returnsClearedCount() {
            // given
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(5L);

            // when
            long cleared = viewCountService.clearAllCounters();

            // then
            assertThat(cleared).isEqualTo(5L);
        }

        @Test
        @DisplayName("스크립트 반환이 null이면 0을 반환한다")
        void returnsZeroWhenScriptReturnsNull() {
            // given
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(null);

            // when
            long cleared = viewCountService.clearAllCounters();

            // then
            assertThat(cleared).isZero();
        }
    }
}
