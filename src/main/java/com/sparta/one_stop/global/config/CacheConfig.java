package com.sparta.one_stop.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
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

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    // Redis 장애 시 캐시 예외를 사용자에게 전달하지 않고 메서드 실행으로 자연스럽게 fallback
    // get/put/evict 모두 로그만 남기고 swallow → 캐시 미스로 간주되어 원본 메서드 호출
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("[Cache] get failed (cache={}, key={}): {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("[Cache] put failed (cache={}, key={}): {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("[Cache] evict failed (cache={}, key={}): {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("[Cache] clear failed (cache={}): {}", cache.getName(), ex.getMessage());
            }
        };
    }

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
    //   - productList : 5분 (상품 목록 — 변경 빈도 ↑)
    //   - default(기본값) : 10분 (productDetail 등 단건 캐시)

    @Bean("redisCacheManager")
    public RedisCacheManager redisCacheManager(
        RedisConnectionFactory connectionFactory,
        ObjectMapper objectMapper
    ) {
        // 캐시 값에 타입 정보(@class)를 함께 직렬화해야 record/POJO 역직렬화가 가능
        // 기본 objectMapper 그대로 쓰면 LinkedHashMap으로 풀려 ClassCastException 발생
        // 비교 사용 사례: enum(UserStatus 등)은 타입 정보 없어도 작동 — 복잡 타입만 영향
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

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
                RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
            )
                    .prefixCacheNameWith("cache:");

        // 캐시별 TTL 차별화
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "userStatus", defaultConfig.entryTtl(Duration.ofMinutes(5)),
            "reviewSummary", defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "productList", defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .transactionAware()
            .build();
    }


}
