package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.request.CategoryCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.CategoryUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.CategoryResponse;
import com.sparta.one_stop.domain.product.dto.response.CategoryTreeResponse;
import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductCategoryMappingRepository;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    // 카테고리 최대 깊이
    private static final int MAX_DEPTH = 3; // 대, 중, 소

    private final CategoryRepository categoryRepository;
    private final ProductCategoryMappingRepository productCategoryMappingRepository;

    // 카테고리 트리 조회 (TTL 1시간 캐시)
    @Cacheable(value = "categories", key = "'tree'")
    public List<CategoryTreeResponse> getTree() {
        List<Category> all = categoryRepository.findAllByOrderByIdAsc();
        return buildTree(all);
    }

    // 카테고리 생성
    @CacheEvict(value = "categories", key = "'tree'")
    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        // 앞뒤 공백만 다른 중복(" 전자"/"전자") 방지 — 검증·저장 모두 정규화된 이름 사용
        String name = request.name().trim();

        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_001));

            // 깊이 검증: 부모 깊이 + 1 이 최대치를 넘으면 거부
            if (parent.getDepth() + 1 > MAX_DEPTH) {
                throw new CustomException(ErrorCode.CATEGORY_003);
            }
        }

        validateNameUnique(parent, name, null);

        try {
            // IDENTITY 전략이라 save 시점에 INSERT가 flush됨 → 동시 생성 경합은 여기서 유니크 위반으로 잡힌다
            Category saved = categoryRepository.save(Category.builder()
                .name(name)
                .parent(parent)
                .build());
            return CategoryResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw toDuplicateNameOrRethrow(e);
        }
    }

    // 카테고리 이름 수정 (부모 이동 미지원)
    @CacheEvict(value = "categories", key = "'tree'")
    @Transactional
    public CategoryResponse updateName(Long categoryId, CategoryUpdateRequest request) {
        String name = request.name().trim();

        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_001));

        validateNameUnique(category.getParent(), name, categoryId);

        category.updateName(name);

        try {
            // 변경(UPDATE)을 지금 강제 flush해 동시 수정 경합을 커밋 전에 유니크 위반으로 잡는다
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw toDuplicateNameOrRethrow(e);
        }
        return CategoryResponse.from(category);
    }

    // (parent_id, name) 유니크 제약(uk_category_parent_name) 위반만 CATEGORY_002로 변환하고,
    // 그 외 무결성 위반은 원인을 숨기지 않도록 그대로 던진다 (AuthCommandService 동일 패턴).
    // 반환 타입을 RuntimeException으로 둬 호출부에서 throw로 흐름을 끊을 수 있게 한다.
    private RuntimeException toDuplicateNameOrRethrow(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        String message = cause != null ? cause.getMessage() : null;
        // 제약명 매칭은 대소문자 무시 — MySQL은 소문자로, H2는 대문자로 제약명을 내보낸다
        if (message != null
            && message.toLowerCase(java.util.Locale.ROOT).contains("uk_category_parent_name")) {
            return new CustomException(ErrorCode.CATEGORY_002);
        }
        return e;
    }

    // 카테고리 삭제 (하위 전체 일괄 삭제, 상품 매핑 있으면 거부)
    @CacheEvict(value = "categories", key = "'tree'")
    @Transactional
    public void delete(Long categoryId) {
        Category target = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_001));

        // 자기 자신 + 모든 후손 ID를 깊이별로 수집
        List<Category> all = categoryRepository.findAllByOrderByIdAsc();
        List<List<Long>> levels = collectSubtreeLevels(target.getId(), all);

        // 서브트리 전체에 상품 매핑이 하나라도 있으면 삭제 불가
        List<Long> allIds = levels.stream().flatMap(List::stream).toList();
        if (productCategoryMappingRepository.existsByCategoryIdIn(allIds)) {
            throw new CustomException(ErrorCode.CATEGORY_004);
        }

        // 자식(깊은 레벨)부터 역순 삭제 - 자기참조 FK 보호
        for (int i = levels.size() - 1; i >= 0; i--) {
            categoryRepository.deleteAllByIdInBatch(levels.get(i));
        }
    }

    // 평면 리스트를 루트 노드 리스트(children 재귀)로 조립
    private List<CategoryTreeResponse> buildTree(List<Category> all) {
        Map<Long, CategoryTreeResponse> nodeMap = new LinkedHashMap<>();
        for (Category c : all) {
            nodeMap.put(c.getId(), new CategoryTreeResponse(c.getId(), c.getName(), new ArrayList<>()));
        }

        List<CategoryTreeResponse> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryTreeResponse node = nodeMap.get(c.getId());
            Long parentId = c.getParent() != null ? c.getParent().getId() : null;
            if (parentId == null) {
                roots.add(node);
            } else {
                nodeMap.get(parentId).children().add(node);
            }
        }
        return roots;
    }

    // 루트 ID 기준 서브트리를 깊이별 ID 묶음으로 수집 (index 0 = 자기 자신)
    private List<List<Long>> collectSubtreeLevels(Long rootId, List<Category> all) {
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (Category c : all) {
            Long parentId = c.getParent() != null ? c.getParent().getId() : null;
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(c.getId());
        }

        List<List<Long>> levels = new ArrayList<>();
        List<Long> current = List.of(rootId);
        while (!current.isEmpty()) {
            levels.add(current);
            List<Long> next = new ArrayList<>();
            for (Long id : current) {
                next.addAll(childrenByParent.getOrDefault(id, List.of()));
            }
            current = next;
        }
        return levels;
    }

    // 같은 부모 아래 이름 중복 검증 (excludeId 있으면 자기 자신 제외)
    private void validateNameUnique(Category parent, String name, Long excludeId) {
        boolean duplicated;
        if (parent == null) {
            duplicated = excludeId == null
                ? categoryRepository.existsByParentIsNullAndName(name)
                : categoryRepository.existsByParentIsNullAndNameAndIdNot(name, excludeId);
        } else {
            duplicated = excludeId == null
                ? categoryRepository.existsByParentIdAndName(parent.getId(), name)
                : categoryRepository.existsByParentIdAndNameAndIdNot(parent.getId(), name, excludeId);
        }
        if (duplicated) {
            throw new CustomException(ErrorCode.CATEGORY_002);
        }
    }
}
