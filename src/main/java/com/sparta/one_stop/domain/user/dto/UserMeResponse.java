package com.sparta.one_stop.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;

import java.time.LocalDateTime;

/**
 * 내 정보 조회 응답 DTO
 *
 * API 명세:
 * {
 * "userId": 1,
 * "email": "hong@example.com",
 * "name": "홍길동",
 * "phone": "010-1234-5678",
 * "address": "서울시 강남구 테헤란로 123",
 * "role": "BUYER",
 * "status": "ACTIVE",
 * "subscription": { "active": true, "endAt": "2026-05-12T00:00:00" },
 * "createdAt": "2025-05-01T10:00:00"
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserMeResponse(
    Long userId,
    String email,
    String name,
    String phone,
    String address,
    UserRole role,
    UserStatus status,
    SubscriptionInfo subscription,
    LocalDateTime createdAt
) {

    /**
     * 구독 정보 내부 DTO
     * 구독 도메인 구현 후 Subscription 엔티티에서 변환
     */
    public record SubscriptionInfo(
        boolean active,
        LocalDateTime endAt
    ) {}

    /** User 엔티티 → 응답 DTO (구독 정보 없음) */
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getPhone(),
            user.getAddress(),
            user.getRole(),
            user.getStatus(),
            null,  // 구독 도메인 연동 시 교체
            user.getCreatedAt()
        );
    }

    /** User + 구독 정보 포함 변환 (구독 도메인 연동 후 사용) */
    public static UserMeResponse of(User user, SubscriptionInfo subscription) {
        return new UserMeResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getPhone(),
            user.getAddress(),
            user.getRole(),
            user.getStatus(),
            subscription,
            user.getCreatedAt()
        );
    }
}
