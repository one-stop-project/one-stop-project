package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.auth.dto.request.SignUpRequest;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService 변경 작업 책임 분리 서비스
 * ═══════════════════════════════════════════════════════════
 *  주요 책임
 * ═══════════════════════════════════════════════════════════
 *  - 회원가입 트랜잭션 (User + Seller 함께 저장)
 *  - saveAndFlush로 동시성 방어 보장
 * 주요 기능
 *  1. saveAndFlush 사용
 *     - saveAndFlush() — 즉시 INSERT → DataIntegrityViolation 정확히 catch
 *  2. AuthService에서 분리
 *     - self-invocation 문제 회피
 *     - 회원가입 트랜잭션이 AuthService 다른 메서드와 격리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCommandService {

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입 — User + Seller 함께 저장
     *
     * 동시성 방어 2단계:
     *   1. 호출자(AuthService)에서 existsByEmail 사전 체크 (UX)
     *   2. 본 메서드에서 saveAndFlush로 UNIQUE 인덱스 충돌 catch (안전성)
     *
     * @throws CustomException AUTH_002 (이메일 중복)
     */
    @Transactional
    public User signup(SignUpRequest request) {
        User user = User.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .name(request.name())
            .phone(request.phone())
            .address(request.address())
            .role(request.role())
            .build();

        User savedUser;
        try {
            // ★ saveAndFlush — 즉시 INSERT 강제 (동시성 방어)
            savedUser = userRepository.saveAndFlush(user);

        } catch (DuplicateKeyException e) {
            log.warn("회원가입 동시성 충돌 — 이메일 중복: {}", maskEmail(request.email()));
            throw new CustomException(ErrorCode.AUTH_002);
        }

        catch (DataIntegrityViolationException e) {
            // JPA UNIQUE 위반은 보통 DIVE로 옴 (DuplicateKeyException이 아닐 수 있음).
            // 이메일 UNIQUE 제약 충돌이면 AUTH_002로 변환, 그 외는 rethrow.
            String msg = e.getMostSpecificCause().getMessage();
            if (msg != null && (msg.contains("email") || msg.toUpperCase().contains("UNIQUE"))) {
                log.warn("회원가입 동시성 충돌(DIVE) — 이메일 중복: {}", maskEmail(request.email()));
                throw new CustomException(ErrorCode.AUTH_002);
            }
            log.error("회원가입 무결성 오류", e);
            throw e;
        }

        // SELLER 추가 정보 저장
        if (request.role() == UserRole.SELLER) {
            Seller seller = Seller.builder()
                .user(savedUser)  // ID 보장 (saveAndFlush 후)
                .shopName(request.shopName())
                .businessNumber(request.businessNumber())
                .bankAccount(request.bankAccount())
                .build();
            sellerRepository.save(seller);
        }

        log.info("회원가입 완료: userId={}, role={}", savedUser.getId(), savedUser.getRole());
        return savedUser;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf("@");
        String id = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (id.length() <= 2) {
            return "*".repeat(id.length()) + domain;
        }
        return id.substring(0, 2) + "*".repeat(id.length() - 2) + domain;
    }
}
