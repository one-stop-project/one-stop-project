package com.sparta.one_stop.dummy.seed;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "dummy.seller.password=TEST_ONLY_DUMMY_SELLER_PASSWORD")
@ActiveProfiles("test")
@Transactional
@DisplayName("DummySellerSeeder")
class DummySellerSeederTest {

    private static final String EMAIL = "ai-shop@dummy.local";
    // @SpringBootTest properties로 주입한 값과 동일 — 기본값(CHANGE_ME) fallback이 아닌 실제 주입을 검증
    private static final String DUMMY_PASSWORD = "TEST_ONLY_DUMMY_SELLER_PASSWORD";

    @Autowired private DummySellerSeeder dummySellerSeeder;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("seed: AI상점 SELLER 생성 (APPROVED, role=SELLER)")
    void seed_createsApprovedSeller() {
        Seller seller = dummySellerSeeder.seed();

        assertThat(seller.getShopName()).isEqualTo("AI상점");
        assertThat(seller.isApproved()).isTrue();
        assertThat(seller.getUser().getEmail()).isEqualTo(EMAIL);
        assertThat(seller.getUser().getRole()).isEqualTo(UserRole.SELLER);
    }

    @Test
    @DisplayName("seed: 외부화한 설정 비밀번호(dummy.seller.password)로 인코딩되어 저장된다")
    void seed_encodesConfiguredPassword() {
        Seller seller = dummySellerSeeder.seed();

        assertThat(passwordEncoder.matches(DUMMY_PASSWORD, seller.getUser().getPassword())).isTrue();
    }

    @Test
    @DisplayName("seed: 두 번 호출해도 멱등 (같은 SELLER)")
    void seed_isIdempotent() {
        Long firstId = dummySellerSeeder.seed().getId();
        em.flush();
        em.clear();  // 커밋 후 재실행 흉내 — 영속성 컨텍스트 비우고 DB 재조회
        Seller second = dummySellerSeeder.seed();

        assertThat(second.getId()).isEqualTo(firstId);
        assertThat(userRepository.findByEmail(EMAIL)).isPresent();
        assertThat(sellerRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("seed: 더미 이메일이 SELLER 아닌 역할로 이미 있으면 예외")
    void seed_throwsWhenEmailExistsWithNonSellerRole() {
        userRepository.save(User.builder()
            .email(EMAIL)
            .password("encoded")
            .name("기존사용자")
            .role(UserRole.BUYER)
            .build());
        em.flush();
        em.clear();

        assertThatThrownBy(() -> dummySellerSeeder.seed())
            .isInstanceOf(IllegalStateException.class);
    }
}
