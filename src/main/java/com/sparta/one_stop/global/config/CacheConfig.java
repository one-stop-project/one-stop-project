package com.sparta.one_stop.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;


//캐시 설정 - Caffeine(로컬) + Redis(분산) 이중 구성
//   Caffeine (로컬 메모리):
//    - 변경 거의 없는 정적 데이터
//    - 다중 인스턴스 일관성 불필요
//    - 예: categories (1시간)
//
//    Redis (분산):
//    - 변경 추적이 필요한 데이터
//    - 다중 인스턴스 일관성 필수
//    - 즉시 evict 필요
//    - 예: userStatus (5분), reviewSummary (30분)
//
//  사용방법 예시)
//   @Cacheable(value = "userStatus", cacheManager = "redisCacheManager")
//   public UserStatus getStatus(Long userId) { ... }
//
//  @CacheEvict(value = "userStatus", cacheManager = "redisCacheManager")
//   public void evictStatus(Long userId) { ... }
//

@Configuration
@EnableCaching
public class CacheConfig {

    // Caffeine : 단일 인스턴스 가정. 다중 인스턴스 확장 시 Redis Pub/Sub 등 도입 필요
    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("categories");
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100));
        return manager;
    }

    // Redis Cache Manager : 캐시별 TTL 차별화
    // 캐시별 TTL 정책
    //   - userStatus : 5분
    //   - reviewSummary : 30분 (AI 토큰 비용 절감)
    //   - default(기본값) : 10분

    @Bean("redisCacheManager")
    public RedisCacheManager redisCacheManager(
        RedisConnectionFactory connectionFactory,
        ObjectMapper objectMapper
    ) {
        // 기본설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl((Duration.ofMinutes(10)))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(objectMapper)
                )
            )
                    .prefixCacheNameWith("cache:");

        // 캐시별 TTL 차별화
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "userStatus", defaultConfig.entryTtl(Duration.ofMinutes(5)),
            "reviewSummary", defaultConfig.entryTtl(Duration.ofMinutes(30))

        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .transactionAware()
            .build();
    }


}
