package com.sparta.one_stop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the build.gradle dependency changes introduced in this PR.
 *
 * This PR removed the following dependency groups from build.gradle:
 * - JWT (io.jsonwebtoken:jjwt-*)
 * - QueryDSL (com.querydsl:querydsl-*)
 * - Swagger/OpenAPI (org.springdoc:springdoc-openapi-*)
 * - Redisson (org.redisson:redisson-spring-boot-starter)
 * - Resilience4j (io.github.resilience4j:*)
 * - Actuator / Micrometer (spring-boot-starter-actuator, micrometer-registry-prometheus)
 *
 * Also removed: the `configurations { compileOnly { extendsFrom annotationProcessor } }` block.
 *
 * These tests verify the classpath reflects the cleaned-up build.
 */
class BuildDependenciesTest {

    @Test
    @DisplayName("JWT library (jjwt-api) is NOT on the classpath after removal from build.gradle")
    void jwtApiIsNotOnClasspath() {
        assertFalse(isClassPresent("io.jsonwebtoken.Jwts"),
                "io.jsonwebtoken.Jwts should not be on the classpath after JWT dependency was removed");
    }

    @Test
    @DisplayName("JJWT Claims class is NOT on the classpath after removal from build.gradle")
    void jwtClaimsIsNotOnClasspath() {
        assertFalse(isClassPresent("io.jsonwebtoken.Claims"),
                "io.jsonwebtoken.Claims should not be on the classpath after JWT dependency was removed");
    }

    @Test
    @DisplayName("QueryDSL JPAQueryFactory is NOT on the classpath after removal from build.gradle")
    void querydslIsNotOnClasspath() {
        assertFalse(isClassPresent("com.querydsl.jpa.impl.JPAQueryFactory"),
                "QueryDSL JPAQueryFactory should not be on the classpath after QueryDSL dependency was removed");
    }

    @Test
    @DisplayName("SpringDoc OpenAPI (Swagger) is NOT on the classpath after removal from build.gradle")
    void swaggerIsNotOnClasspath() {
        assertFalse(isClassPresent("org.springdoc.core.models.GroupedOpenApi"),
                "SpringDoc OpenAPI should not be on the classpath after Swagger dependency was removed");
    }

    @Test
    @DisplayName("Redisson client is NOT on the classpath after removal from build.gradle")
    void redissonIsNotOnClasspath() {
        assertFalse(isClassPresent("org.redisson.api.RedissonClient"),
                "Redisson should not be on the classpath after Redisson dependency was removed");
    }

    @Test
    @DisplayName("Resilience4j CircuitBreaker is NOT on the classpath after removal from build.gradle")
    void resilience4jIsNotOnClasspath() {
        assertFalse(isClassPresent("io.github.resilience4j.circuitbreaker.CircuitBreaker"),
                "Resilience4j should not be on the classpath after Resilience4j dependency was removed");
    }

    @Test
    @DisplayName("Micrometer Prometheus registry is NOT on the classpath after removal from build.gradle")
    void micrometerPrometheusIsNotOnClasspath() {
        assertFalse(isClassPresent("io.micrometer.prometheusmetrics.PrometheusConfig"),
                "Micrometer Prometheus registry should not be on the classpath after removal");
    }

    @Test
    @DisplayName("Core Spring Boot dependencies remain on the classpath")
    void springBootCoreIsOnClasspath() {
        assertTrue(isClassPresent("org.springframework.boot.SpringApplication"),
                "Spring Boot core must remain on the classpath");
    }

    @Test
    @DisplayName("Spring Security remains on the classpath")
    void springSecurityIsOnClasspath() {
        assertTrue(isClassPresent("org.springframework.security.config.annotation.web.builders.HttpSecurity"),
                "Spring Security must remain on the classpath");
    }

    @Test
    @DisplayName("Spring Data JPA remains on the classpath")
    void springDataJpaIsOnClasspath() {
        assertTrue(isClassPresent("org.springframework.data.jpa.repository.JpaRepository"),
                "Spring Data JPA must remain on the classpath");
    }

    @Test
    @DisplayName("Spring Validation remains on the classpath")
    void springValidationIsOnClasspath() {
        assertTrue(isClassPresent("jakarta.validation.constraints.NotNull"),
                "Spring Validation (jakarta.validation) must remain on the classpath");
    }

    @Test
    @DisplayName("Lombok remains available for compilation")
    void lombokIsOnClasspath() {
        assertTrue(isClassPresent("lombok.Getter"),
                "Lombok must remain on the classpath");
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
