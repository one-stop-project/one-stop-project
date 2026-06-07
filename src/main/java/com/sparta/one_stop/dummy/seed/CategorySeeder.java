package com.sparta.one_stop.dummy.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 더미 카테고리 84노드(대6/중18/소60) 시드.
// dummy/categories.json 트리를 부모 걸며 생성, 멱등((parent_id, name) 기준 기존 재사용)
@Component
@RequiredArgsConstructor
public class CategorySeeder {

    private static final String CATEGORY_RESOURCE = "dummy/categories.json";

    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void seed() {
        Map<String, Category> existing = loadExisting();
        for (CategoryNode root : loadTree()) {
            seedNode(root, null, existing);
        }
    }

    private void seedNode(CategoryNode node, Category parent, Map<String, Category> existing) {
        Long parentId = (parent == null) ? null : parent.getId();
        Category category = existing.get(key(parentId, node.name()));
        if (category == null) {
            category = categoryRepository.save(
                Category.builder().name(node.name()).parent(parent).build());
            existing.put(key(parentId, node.name()), category);
        }
        for (CategoryNode child : node.children()) {
            seedNode(child, category, existing);
        }
    }

    // 기존 카테고리를 (parent_id, name) 키로 미리 적재 → 멱등 판정
    private Map<String, Category> loadExisting() {
        Map<String, Category> map = new HashMap<>();
        for (Category c : categoryRepository.findAll()) {
            Long parentId = (c.getParent() == null) ? null : c.getParent().getId();
            map.put(key(parentId, c.getName()), c);
        }
        return map;
    }

    private List<CategoryNode> loadTree() {
        try (InputStream in = new ClassPathResource(CATEGORY_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<CategoryNode>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("카테고리 시드 데이터(" + CATEGORY_RESOURCE + ") 로드 실패", e);
        }
    }

    private String key(Long parentId, String name) {
        return parentId + "::" + name;
    }
}
