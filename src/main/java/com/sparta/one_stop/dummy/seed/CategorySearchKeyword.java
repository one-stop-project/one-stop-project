package com.sparta.one_stop.dummy.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 잎 카테고리 → 네이버 쇼핑 검색어 해석.
// categories.json의 searchKeyword(경로 기준)가 있으면 그걸 쓰고, 없으면 카테고리명에서 파생한다.
// 경로로 키잉해 같은 이름이 다른 부모 아래 있어도 충돌하지 않는다.
// 더미 시드 활성(naver.api.client-id) 환경에서만 빈으로 뜬다.
@Component
@ConditionalOnProperty(prefix = "naver.api", name = "client-id")
@RequiredArgsConstructor
public class CategorySearchKeyword {

    private static final String CATEGORY_RESOURCE = "dummy/categories.json";
    private static final String PATH_SEP = " > ";

    private final ObjectMapper objectMapper;
    private Map<String, String> overridesByPath = Map.of();

    @PostConstruct
    void load() {
        Map<String, String> map = new HashMap<>();
        for (CategoryNode root : loadTree()) {
            collect(root, "", map);
        }
        overridesByPath = map;
    }

    // 카테고리 경로(루트→잎 이름 순)에 대응하는 검색어. 명시값(searchKeyword) 우선, 없으면 잎 이름에서 파생.
    public String resolve(List<String> pathNamesRootToLeaf) {
        if (pathNamesRootToLeaf == null || pathNamesRootToLeaf.isEmpty()) {
            return null;
        }
        String explicit = overridesByPath.get(String.join(PATH_SEP, pathNamesRootToLeaf));
        if (explicit != null) {
            return explicit;
        }
        return deriveFromName(pathNamesRootToLeaf.get(pathNamesRootToLeaf.size() - 1));
    }

    // 카테고리명 → 검색어: 첫 구분자(· / , &) 앞 토큰만 사용하고 공백을 정리한다.
    // (구분자를 공백으로 바꿔 다중 토큰으로 검색하면 결과가 좁아지므로 대표 토큰 하나만 쓴다)
    static String deriveFromName(String name) {
        if (name == null) {
            return null;
        }
        String first = name.split("[·,/&]", 2)[0];
        return first.replaceAll("\\s+", " ").trim();
    }

    private void collect(CategoryNode node, String parentPath, Map<String, String> map) {
        String path = parentPath.isEmpty() ? node.name() : parentPath + PATH_SEP + node.name();
        if (node.children().isEmpty()) {
            if (node.searchKeyword() != null && !node.searchKeyword().isBlank()) {
                map.put(path, node.searchKeyword().trim());
            }
        } else {
            for (CategoryNode child : node.children()) {
                collect(child, path, map);
            }
        }
    }

    private List<CategoryNode> loadTree() {
        try (InputStream in = new ClassPathResource(CATEGORY_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<CategoryNode>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("카테고리 검색어 데이터(" + CATEGORY_RESOURCE + ") 로드 실패", e);
        }
    }
}
