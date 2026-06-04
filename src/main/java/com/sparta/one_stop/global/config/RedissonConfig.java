package com.sparta.one_stop.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 실제 운영 환경이라면 여기서 cluster 설정 등을 추가할 수 있습니다.
        config.useSingleServer()
            .setAddress("redis://" + redisHost + ":" + redisPort);
        // 필요하다면 여기서 .setConnectionPoolSize() 등을 추가 튜닝하세요.

        return Redisson.create(config);
    }
}
