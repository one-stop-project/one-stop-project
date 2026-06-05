package com.sparta.one_stop.dummy.seed;

import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CategorySeeder")
class CategorySeederTest {

    @Autowired private CategorySeeder categorySeeder;
    @Autowired private CategoryRepository categoryRepository;
    @PersistenceContext private EntityManager em;

    @Test
    @DisplayName("seed: 84노드 생성 + 트리 부모 연결 (대6/잎60)")
    void seed_creates84NodesWithTree() {
        categorySeeder.seed();

        List<Category> all = categoryRepository.findAll();
        assertThat(all).hasSize(84);

        long roots = all.stream().filter(c -> c.getParent() == null).count();
        assertThat(roots).isEqualTo(6);

        Set<Long> parentIds = all.stream()
            .map(Category::getParent).filter(Objects::nonNull)
            .map(Category::getId).collect(Collectors.toSet());
        long leaves = all.stream().filter(c -> !parentIds.contains(c.getId())).count();
        assertThat(leaves).isEqualTo(60);

        // 트리 연결: 과일 → 신선식품 → 식품(루트)
        Category fruit = findByName(all, "과일");
        assertThat(fruit.getParent().getName()).isEqualTo("신선식품");
        assertThat(fruit.getParent().getParent().getName()).isEqualTo("식품");
        assertThat(fruit.getParent().getParent().getParent()).isNull();
    }

    @Test
    @DisplayName("seed: 두 번 호출해도 멱등 (중복 생성 없음)")
    void seed_isIdempotent() {
        categorySeeder.seed();
        em.flush();
        em.clear();  // 커밋 후 재실행 흉내 — 영속성 컨텍스트 비우고 DB 재조회
        categorySeeder.seed();

        assertThat(categoryRepository.findAll()).hasSize(84);
    }

    private Category findByName(List<Category> all, String name) {
        return all.stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }
}
