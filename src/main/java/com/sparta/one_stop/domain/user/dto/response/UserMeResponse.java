package com.sparta.one_stop.domain.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;

import java.time.LocalDateTime;

/**
 * 내 정보 조회 응답 DTO
 *
 * {
 *   "userId": 1, "email": "...", "name": "...", "phone": "...", "address": "...",
 *   "role": "BUYER", "status": "ACTIVE",
 *   "social": true,            // 분기용 — 항상 존재
 *   "provider": "kakao",       // 표시용 — 소셜 계정만 존재
 *   "subscription": { ... }, "createdAt": "..."
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserMeResponse(
    Long userId, String email, String name, String phone, String address, String detailAddress,
    UserRole role, UserStatus status,
    boolean social,
    String provider,
    SubscriptionInfo subscription, LocalDateTime createdAt
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
            user.getId(), user.getEmail(), user.getName(), user.getPhone(),
            user.getAddress(), user.getDetailAddress(), user.getRole(), user.getStatus(),
            user.isOAuth2User(), user.getProvider(),
            null, user.getCreatedAt());
    }

    public static UserMeResponse of(User user, SubscriptionInfo subscription) {
        return new UserMeResponse(
            user.getId(), user.getEmail(), user.getName(), user.getPhone(),
            user.getAddress(), user.getDetailAddress(), user.getRole(), user.getStatus(),
            user.isOAuth2User(), user.getProvider(),
            subscription, user.getCreatedAt());
    }

    public record SubscriptionInfo(boolean active, LocalDateTime endAt) {}
}
