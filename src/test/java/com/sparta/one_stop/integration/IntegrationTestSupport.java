package com.sparta.one_stop.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트 공통 설정 클래스
 *
 * 목적:
 * - 여러 통합 테스트에서 공통으로 사용할 SpringBootTest, test profile, MySQL/Redis Testcontainers 설정을 제공한다.
 * - 실제 DB/Redis 환경에 가까운 상태에서 Repository, Service, Transaction, Cache/Redis 흐름을 검증하기 위해 사용한다.
 *
 * 테스트 환경 정책:
 * - application-test.yml은 기존 단위 테스트와 컨텍스트 테스트를 위해 H2/localhost Redis 설정을 유지한다.
 * - 통합 테스트만 이 클래스를 상속하여 Testcontainers MySQL/Redis를 사용한다.
 * - RedisTemplate은 테스트 전용 LettuceConnectionFactory를 우선 사용한다.
 * - RedissonClient가 필요한 테스트에서는 테스트 클래스에서 mock 또는 별도 설정으로 제어한다.
 * - 통합 테스트에서는 테스트 데이터를 직접 구성하므로 더미 시더를 비활성화한다.
 * - 테스트 안정성을 위해 Batch Job 자동 실행과 Kafka Listener 자동 시작을 비활성화한다.
 *
 * 주의사항:
 * - @Transactional이 적용되어 각 테스트 종료 후 DB 변경 사항은 롤백된다.
 * - Redis 데이터는 트랜잭션 롤백 대상이 아니므로, Redis를 사용하는 테스트에서는 테스트별 key를 직접 삭제하거나
 *   고유한 guestCartId/key를 사용해야 한다.
 */
@SpringBootTest(properties = {
    // RedisTemplate이 RedissonConnectionFactory를 사용하지 않도록 Redisson 자동 구성을 제외한다.
    "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2"
})
@ActiveProfiles("test")
@Transactional
@Import(IntegrationTestSupport.TestRedisConfig.class)
public abstract class IntegrationTestSupport {

    protected static final GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);
    private static final MySQLContainer<?> MYSQL_CONTAINER =
        new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("one_stop_test")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
        // Testcontainers MySQL DataSource 설정
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);

        // 실제 MySQL 기준으로 Hibernate DDL 생성
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add(
            "spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.MySQLDialect"
        );

        // Testcontainers Redis 설정
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));

        // 통합 테스트에서는 필요한 데이터를 테스트 코드에서 직접 구성하므로 더미 시더 비활성화
        registry.add("test-data.init", () -> "false");

        // 통합 테스트 컨텍스트 로딩 중 Batch Job이 자동 실행되지 않도록 방지
        registry.add("spring.batch.job.enabled", () -> "false");

        // Batch 관련 Bean이 메타 테이블을 필요로 할 수 있으므로 테스트 DB에 Batch 스키마 자동 생성
        registry.add("spring.batch.jdbc.initialize-schema", () -> "always");

        // Kafka Consumer가 테스트 중 자동으로 떠서 외부 Kafka에 연결하지 않도록 방지
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @TestConfiguration
    static class TestRedisConfig {

        /**
         * 통합 테스트 전용 RedisConnectionFactory
         *
         * RedissonConnectionFactory 대신 LettuceConnectionFactory를 우선 사용하도록 등록한다.
         * RedisTemplate은 이 ConnectionFactory를 통해 Redis Testcontainer에 연결된다.
         */
        @Bean
        @Primary
        RedisConnectionFactory redisConnectionFactory() {
            RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(
                    REDIS_CONTAINER.getHost(),
                    REDIS_CONTAINER.getMappedPort(6379)
                );

            return new LettuceConnectionFactory(configuration);
        }
    }

}
