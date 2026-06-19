package com.sparta.one_stop.dummy.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CategorySearchKeyword - 카테고리 검색어 해석")
class CategorySearchKeywordTest {

    private CategorySearchKeyword resolver;

    @BeforeEach
    void setUp() {
        resolver = new CategorySearchKeyword(new ObjectMapper());
        resolver.load();
    }

    @Test
    @DisplayName("searchKeyword가 지정된 잎은 그 검색어를 쓴다 (도서·반려동물 맥락 보정) (#527)")
    void explicitKeyword_isUsed() {
        assertThat(resolver.resolve(List.of("도서", "교양·기타", "만화"))).isEqualTo("만화책");
        assertThat(resolver.resolve(List.of("반려동물 용품", "용품", "장난감"))).isEqualTo("강아지 장난감");
        assertThat(resolver.resolve(List.of("도서", "실용·전문", "IT·컴퓨터"))).isEqualTo("컴퓨터 도서");
    }

    @Test
    @DisplayName("searchKeyword 없는 잎은 이름의 첫 토큰을 검색어로 쓴다 (#527)")
    void noKeyword_fallsBackToFirstToken() {
        assertThat(resolver.resolve(List.of("전자제품", "음향·주변기기", "키보드·마우스"))).isEqualTo("키보드");
        assertThat(resolver.resolve(List.of("식품", "신선식품", "정육·계란"))).isEqualTo("정육");
    }

    @Test
    @DisplayName("단순 이름은 그대로 검색어가 된다 (#527)")
    void simpleName_passthrough() {
        assertThat(resolver.resolve(List.of("식품", "신선식품", "과일"))).isEqualTo("과일");
        assertThat(resolver.resolve(List.of("전자제품", "모바일·PC", "스마트폰"))).isEqualTo("스마트폰");
    }

    @Test
    @DisplayName("경로가 null이거나 비면 null을 반환한다 — 오케스트레이터가 빈 카테고리로 건너뛴다 (#527)")
    void nullOrEmptyPath_returnsNull() {
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve(List.of())).isNull();
    }

    @Test
    @DisplayName("deriveFromName: 첫 구분자 앞 토큰만 사용하고 공백을 정리한다")
    void deriveFromName_firstToken() {
        assertThat(CategorySearchKeyword.deriveFromName("이어폰·헤드폰")).isEqualTo("이어폰");
        assertThat(CategorySearchKeyword.deriveFromName("속옷·양말")).isEqualTo("속옷");
        assertThat(CategorySearchKeyword.deriveFromName("과일")).isEqualTo("과일");
    }
}
