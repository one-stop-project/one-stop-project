package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 상품 수정 요청 검증 — 부분 수정(null=변경 안 함)은 허용하되 빈 문자열·공백 값은 거부 (#408)
@DisplayName("ProductUpdateRequest - name/description 공백 거부 검증")
class ProductUpdateRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    @DisplayName("name 빈 문자열은 거부된다")
    void name_blankString_rejected() {
        assertThat(violatedFields(request("", "정상 설명"))).contains("name");
    }

    @Test
    @DisplayName("name 공백만 있는 값도 거부된다")
    void name_whitespaceOnly_rejected() {
        assertThat(violatedFields(request("   ", "정상 설명"))).contains("name");
    }

    @Test
    @DisplayName("name null은 허용된다 (부분 수정 = 변경 안 함)")
    void name_null_allowed() {
        assertThat(violatedFields(request(null, "정상 설명"))).doesNotContain("name");
    }

    @Test
    @DisplayName("name 200자는 통과, 201자는 길이 초과로 거부된다")
    void name_lengthBoundary() {
        assertThat(violatedFields(request("가".repeat(200), "정상 설명"))).doesNotContain("name");
        assertThat(violatedFields(request("가".repeat(201), "정상 설명"))).contains("name");
    }

    @Test
    @DisplayName("description 빈 문자열은 거부된다")
    void description_blankString_rejected() {
        assertThat(violatedFields(request("정상 상품명", ""))).contains("description");
    }

    @Test
    @DisplayName("description 공백만 있는 값도 거부된다")
    void description_whitespaceOnly_rejected() {
        assertThat(violatedFields(request("정상 상품명", "   "))).contains("description");
    }

    @Test
    @DisplayName("description null은 허용된다")
    void description_null_allowed() {
        assertThat(violatedFields(request("정상 상품명", null))).doesNotContain("description");
    }

    @Test
    @DisplayName("description 줄바꿈 포함 정상 값은 통과된다")
    void description_multiline_allowed() {
        assertThat(violatedFields(request("정상 상품명", "첫째 줄\n둘째 줄"))).doesNotContain("description");
    }

    @Test
    @DisplayName("name 유니코드 공백(전각 공백)만 있는 값도 거부된다")
    void name_unicodeWhitespace_rejected() {
        String fullWidthSpace = Character.toString(0x3000);   // 전각 공백 U+3000
        assertThat(violatedFields(request(fullWidthSpace, "정상 설명"))).contains("name");
    }

    @Test
    @DisplayName("description 유니코드 공백(NBSP)만 있는 값도 거부된다")
    void description_unicodeWhitespace_rejected() {
        String nbsp = Character.toString(0x00A0);   // non-breaking space U+00A0
        assertThat(violatedFields(request("정상 상품명", nbsp))).contains("description");
    }

    @Test
    @DisplayName("name·description 모두 정상이면 위반 없음")
    void allValid_noViolation() {
        assertThat(validator.validate(request("정상 상품명", "정상 설명"))).isEmpty();
    }

    private ProductUpdateRequest request(String name, String description) {
        ProductUpdateRequest r = new ProductUpdateRequest();   // protected 기본 생성자 — 동일 패키지라 접근 가능
        ReflectionTestUtils.setField(r, "name", name);
        ReflectionTestUtils.setField(r, "description", description);
        return r;
    }

    private Set<String> violatedFields(ProductUpdateRequest request) {
        return validator.validate(request).stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(Collectors.toSet());
    }
}
