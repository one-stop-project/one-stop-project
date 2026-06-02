package com.sparta.one_stop;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class OneStopApplicationTests {

    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }
}
