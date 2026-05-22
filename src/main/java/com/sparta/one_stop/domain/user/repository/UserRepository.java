package com.sparta.one_stop.domain.user.repository;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // ── 기본 조회 ──
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // ── N+1 방어 ──
    //SELLER 로그인 시 사용 — Seller 정보 한 번에 fetch, Hibernate가 LEFT JOIN으로 1회 쿼리만 발생
    @EntityGraph(attributePaths = {"seller"})
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailWithSeller(@Param("email") String email);

    // ── OAuth2 ──
    //OAuth2 Provider 매칭 — UNIQUE (provider, provider_id) 인덱스 활용
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    // ── 가벼운 조회 (Redis 캐싱 대상) ──
    // User 상태만 조회 — 캐싱하기 좋은 가벼운 쿼리, 활성 상태 검증 시 User 전체 가져올 필요 없음
    @Query("SELECT u.status FROM User u WHERE u.id = :userId")
    Optional<UserStatus> findStatusById(@Param("userId") Long userId);

    // ── 관리자/통계 ──
    long countByStatus(UserStatus status);
}
