package com.sparta.one_stop.domain.user.service;


import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.domain.user.dto.request.PasswordChangeRequest;
import com.sparta.one_stop.domain.user.dto.request.UserUpdateRequest;
import com.sparta.one_stop.domain.user.dto.request.WithdrawRequest;
import com.sparta.one_stop.domain.user.dto.response.UserMeResponse;
import com.sparta.one_stop.domain.user.dto.response.UserUpdateResponse;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTokenService redisTokenService;


    // 내 정보 조회
    @Transactional(readOnly = true)
    public UserMeResponse getMyInfo(Long userId) {
        User user = findUserById(userId);
        // TODO: 구독 도메인 구현 후 SubscriptionInfo 연동
        //   예시문 : Subscription sub = subscriptionRepository.findActiveByUserId(userId);
        //   예시문 : return UserMeResponse.of(user, toSubscriptionInfo(sub));
        return UserMeResponse.from(user);
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
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.MEMBER_003);
        }

        // 3. 비밀번호 변경
        user.changePassword(passwordEncoder.encode(request.newPassword()));

        // 4. [보안 정책] 모든 기기 강제 로그아웃 — 변경 사실을 다른 기기에서 강제 인지시킴
        redisTokenService.deleteAllRefreshTokensByUserId(userId);

        log.info("비밀번호 변경 (전체 세션 무효화): userId={}", userId);
    }

    // 회원 탈퇴
    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = findUserById(userId);

        // 1. 비밀번호 재확인 (실수/타인 조작 방지)
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.MEMBER_002);
        }

        // 2. Soft Delete (status = WITHDRAWN)
        user.withdraw();

        // 3. 모든 기기 RT 일괄 삭제 (다중 기기 키 패턴: RT:{userId}:*)
        redisTokenService.deleteAllRefreshTokensByUserId(userId);

        log.info("회원 탈퇴: userId={}", userId);
    }

    // ── 내부 헬퍼 ──

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));
    }

}
