package com.sparta.one_stop.dummy.seed;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("DummySellerSeeder")
class DummySellerSeederTest {

    private static final String EMAIL = "ai-shop@dummy.local";

    @Autowired private DummySellerSeeder dummySellerSeeder;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

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
}
