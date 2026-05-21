package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 여러 카테고리 ID로 한 번에 조회
    List<Category> findAllByIdIn(List<Long> ids);

    // 트리 조립용 - 등록순(categoryId ASC) 전체 조회
    List<Category> findAllByOrderByIdAsc();

    // 같은 부모 아래 이름 중복 검증 (자식 노드)
    boolean existsByParentIdAndName(Long parentId, String name);

    // 같은 부모 아래 이름 중복 검증 (루트 노드)
    boolean existsByParentIsNullAndName(String name);

    // 이름 수정 시 중복 검증 (자기 자신 제외, 자식 노드)
    boolean existsByParentIdAndNameAndIdNot(Long parentId, String name, Long id);

    // 이름 수정 시 중복 검증 (자기 자신 제외, 루트 노드)
    boolean existsByParentIsNullAndNameAndIdNot(String name, Long id);
}
