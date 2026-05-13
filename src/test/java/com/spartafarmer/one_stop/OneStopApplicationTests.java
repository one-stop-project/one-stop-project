package com.spartafarmer.one_stop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;

/**
 * Application context integration tests.
 *
 * This PR removed several auto-configuration classes (SecurityConfig, JwtTokenProvider,
 * exception handlers, response wrappers) and external library dependencies (JWT, QueryDSL,
 * Swagger, Redisson, Resilience4j, Monitoring). These tests verify the application
 * bootstraps correctly with the simplified build.gradle.
 *
 * Uses H2 in-memory database (test scope) and excludes Redis auto-config so that
 * no external services are required to run the test suite.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class OneStopApplicationTests {

    @Test
    @DisplayName("Application context loads successfully after removal of JWT/QueryDSL/Swagger dependencies")
    void contextLoads() {
        // Verifies that removing JWT, QueryDSL, Swagger, Redisson, Resilience4j,
        // and Monitoring dependencies from build.gradle does not break application startup.
    }
}
