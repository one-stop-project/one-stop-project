package com.sparta.one_stop.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트 공통 설정 클래스
 * 목적:
 * - 여러 통합 테스트에서 공통으로 사용할 SpringBootTest, test profile, MySQL Testcontainers 설정을 제공한다.
 * - 실제 MySQL 환경에 가까운 DB에서 Repository, Service, Transaction 흐름을 검증하기 위해 사용한다.
 * 사용 방법:
 * - MySQL 기반 통합 테스트 클래스는 이 클래스를 상속한다.
 * 예시:
 * class CartOrderPaymentIntegrationTest extends IntegrationTestSupport {...}
 *
 * 테스트 환경 정책:
 * - application-test.yml은 기존 단위 테스트와 컨텍스트 테스트를 위해 H2 설정을 유지한다.
 * - MySQL이 필요한 통합 테스트만 이 클래스를 상속하여 Testcontainers MySQL을 사용한다.
 * - 통합 테스트에서는 테스트 데이터를 직접 구성하므로 더미 시더를 비활성화한다.
 * - 테스트 안정성을 위해 Batch Job 자동 실행과 Kafka Listener 자동 시작을 비활성화한다.
 * 주의사항:
 * - @Transactional이 적용되어 각 테스트 종료 후 DB 변경 사항은 롤백된다.
 * - afterCommit 콜백, 실제 커밋 이후 동작을 검증해야 하는 테스트에서는
 * - 클래스 또는 테스트 메서드 단위로 @Transactional 제거를 검토해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

    /**
     * 통합 테스트 전용 MySQL Testcontainer
     * static 필드로 관리하여 통합 테스트 컨텍스트에서 하나의 MySQL 컨테이너를 재사용한다.
     * 이를 통해 테스트 클래스마다 컨테이너를 새로 띄우는 비용을 줄인다.
     */
    private static final MySQLContainer<?> MYSQL_CONTAINER =
        new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("one_stop_test")
            .withUsername("test")
            .withPassword("test");

    /**
     * Spring ApplicationContext가 생성되기 전에 MySQL 컨테이너를 시작한다.
     * DynamicPropertySource에서 컨테이너의 JDBC URL, username, password를 주입해야 하므로
     * 컨테이너가 먼저 실행되어 있어야 한다.
     */
    static {
        MYSQL_CONTAINER.start();
    }

    /**
     * 통합 테스트 실행 시 사용할 동적 프로퍼티를 등록한다.
     * 여기서 등록한 설정은 application-test.yml보다 우선 적용된다.
     * 따라서 기존 test profile은 유지하면서, 이 클래스를 상속한 테스트만
     * H2 대신 Testcontainers MySQL을 사용하게 된다.
     */
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

        // 통합 테스트에서는 필요한 데이터를 테스트 코드에서 직접 구성하므로 더미 시더 비활성화
        registry.add("test-data.init", () -> "false");

        // 통합 테스트 컨텍스트 로딩 중 Batch Job이 자동 실행되지 않도록 방지
        registry.add("spring.batch.job.enabled", () -> "false");

        // Batch 관련 Bean이 메타 테이블을 필요로 할 수 있으므로 테스트 DB에 Batch 스키마 자동 생성
        registry.add("spring.batch.jdbc.initialize-schema", () -> "always");

        // Kafka Consumer가 테스트 중 자동으로 떠서 외부 Kafka에 연결하지 않도록 방지
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

}
