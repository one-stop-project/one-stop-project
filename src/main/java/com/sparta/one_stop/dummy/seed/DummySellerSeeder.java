package com.sparta.one_stop.dummy.seed;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 더미 상품 소유용 SELLER 1개("AI상점", APPROVED) 시드.
// 엔티티 직접 build(TestDataInitializer 선례) + 멱등(고정 email/userId 존재 시 재사용)
@Component
@RequiredArgsConstructor
public class DummySellerSeeder {

    private static final String EMAIL = "ai-shop@dummy.local";
    private static final String SHOP_NAME = "AI상점";
    private static final String BUSINESS_NUMBER = "000-00-00000";
    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;

    // 더미 판매자 비밀번호는 소스에 평문으로 두지 않고 env(dummy.seller.password)로 외부화한다.
    // 미설정 시 placeholder로 생성되며, 기존 계정은 최초 생성 이후 비밀번호를 갱신하지 않는다.
    @Value("${dummy.seller.password:CHANGE_ME}")
    private String rawPassword;

    @Transactional
    public Seller seed() {
        User user = userRepository.findByEmail(EMAIL)
            .map(this::requireSellerRole)
            .orElseGet(() -> userRepository.save(User.builder()
                .email(EMAIL)
                .password(passwordEncoder.encode(rawPassword))
                .name(SHOP_NAME)
                .role(UserRole.SELLER)
                .build()));

        Seller seller = sellerRepository.findByUserId(user.getId())
            .orElseGet(() -> sellerRepository.save(Seller.builder()
                .user(user)
                .shopName(SHOP_NAME)
                .businessNumber(BUSINESS_NUMBER)
                .build()));

        // 신규(PENDING)·기존(미승인) 모두 승인 상태 보장
        if (!seller.isApproved()) {
            seller.approve();
        }
        // 반환 후 트랜잭션 밖에서 getUser() 접근해도 안전하게 lazy 연관 초기화
        seller.getUser().getEmail();
        return seller;
    }

    // 고정 더미 email이 SELLER 아닌 역할로 이미 존재하면 시드 중단 (정합성 보호)
    private User requireSellerRole(User user) {
        if (user.getRole() != UserRole.SELLER) {
            throw new IllegalStateException(
                "더미 SELLER 이메일(" + EMAIL + ")이 SELLER 아닌 역할로 존재: " + user.getRole());
        }
        return user;
    }
}
