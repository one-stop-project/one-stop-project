package com.sparta.one_stop.domain.user.service;


import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.domain.user.dto.request.PasswordChangeRequest;
import com.sparta.one_stop.domain.user.dto.request.UserUpdateRequest;
import com.sparta.one_stop.domain.user.dto.request.WithdrawRequest;
import com.sparta.one_stop.domain.user.dto.response.UserMeResponse;
import com.sparta.one_stop.domain.user.dto.response.UserUpdateResponse;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final UserStatusCacheService userStatusCacheService;
    private final SubscriptionRepository subscriptionRepository;

    // 내 정보 조회
    @Transactional(readOnly = true)
    public UserMeResponse getMyInfo(Long userId) {
        User user = findUserById(userId);
        List<SubscriptionStatus> validStatuses =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED);
        Subscription subscription =
            subscriptionRepository
                .findTopByUserIdAndStatusInOrderByCreatedAtDesc(userId, validStatuses)
                .orElse(null);
        return UserMeResponse.of(user, toSubscriptionInfo(subscription));
    }

    // 구독 정보 DTO 변환
    private UserMeResponse.SubscriptionInfo toSubscriptionInfo(Subscription subscription) {
        if (subscription == null) {
            return null;
        }
        boolean active = subscription.getStatus() == SubscriptionStatus.ACTIVE;
        return new UserMeResponse.SubscriptionInfo(
            active,
            subscription.getEndAt()
        );
    }

    @Transactional
    public UserUpdateResponse updateMyInfo(Long userId, UserUpdateRequest request) {
        User user = findUserById(userId);
        user.updateProfile(request.name(), request.phone(), request.address());
        log.info("회원정보 수정: userId={}", userId);
        return UserUpdateResponse.from(user);

    }


    // 비밀번호 변경
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = findUserById(userId);

        // 1. 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.MEMBER_002);
        }

        // 2. 새 비밀번호가 현재와 동일한지 확인
        if (request.currentPassword().equals(request.newPassword())) {
            throw new CustomException(ErrorCode.MEMBER_003);
        }

        // 3. 인코딩 + Entity 변경
        String newEncoded = passwordEncoder.encode(request.newPassword());
        user.changePassword(newEncoded);

        // tokenVersion++ (DB 영속 무효화) — 캐시 evict는 커밋 후 Listener가 수행
        user.increaseTokenVersion();

        // 4. 이벤트 발행 — DB 커밋 후 Listener가 Redis 정리
        eventPublisher.publishEvent(new AllDevicesLogoutEvent(userId, "PASSWORD_CHANGED"));

        log.info("비밀번호 변경 (전체 세션 무효화): userId={}", userId);
    }

    // 회원 탈퇴
    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = findUserById(userId);

        // OAuth2 사용자는 비밀번호 검증 스킵
        if (!user.isOAuth2User()) {
            if (!passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new CustomException(ErrorCode.MEMBER_002);
            }
        }

        // Soft Delete (status = WITHDRAWN)
        user.withdraw();

        // tokenVersion++ (DB 영속 무효화)
        user.increaseTokenVersion();

        // 캐시 무효화 (status) — tokenVersion 캐시는 커밋 후 Listener가 evict
        userStatusCacheService.evict(userId);
        userStatusCacheService.evictTokenVersion(userId);

        // 이벤트 발행 — Redis 정리 + 알림 발송 등
        eventPublisher.publishEvent(new AllDevicesLogoutEvent(userId, "WITHDRAWN"));

        log.info("회원 탈퇴: userId={}, isOAuth2={}", userId, user.isOAuth2User());
    }

    // 관리자용 - 정지/해제 (향후 추가 시)
    @Transactional
    public void suspendUser(Long userId, String reason) {
        User user = findUserById(userId);
        user.suspend();

        // tokenVersion++ (DB 영속 무효화)
        user.increaseTokenVersion();

        // 캐시 무효화 (status) — tokenVersion 캐시는 커밋 후 Listener가 evict
        userStatusCacheService.evict(userId);
        userStatusCacheService.evictTokenVersion(userId);

        //정지된 사용자 전체 세션 무효화
        eventPublisher.publishEvent(new AllDevicesLogoutEvent(userId, "SUSPENDED"));
        log.info("회원 정지: userId={}, reason={}", userId, reason);

    }

    @Transactional
    public void reactivateUser(Long userId) {
        User user = findUserById(userId);
        user.reactivate();

        // 캐시 무효화 - status가 ACTIVE로 변경
        userStatusCacheService.evict(userId);

        log.info("회원 정지 해제: userId ={}", userId);
    }

    // ── 내부 헬퍼 ──

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));
    }

}
