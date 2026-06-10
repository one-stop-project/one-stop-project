package com.sparta.one_stop.domain.product.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FulltextIndexInitializer")
class FulltextIndexInitializerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final FulltextIndexInitializer initializer = new FulltextIndexInitializer(jdbcTemplate);

    @Test
    @DisplayName("인덱스가 이미 있으면 생성(ALTER) 안 한다")
    void skipsWhenIndexExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);

        initializer.ensureFulltextIndex();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("인덱스가 없으면 FULLTEXT 인덱스를 생성한다")
    void createsWhenIndexMissing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);

        initializer.ensureFulltextIndex();

        verify(jdbcTemplate).execute(contains("ADD FULLTEXT INDEX"));
    }

    @Test
    @DisplayName("생성 중 예외가 나도 기동을 막지 않고, 재확인 후 분류한다")
    void swallowsExceptionAndRechecks() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        doThrow(new RuntimeException("DB 오류")).when(jdbcTemplate).execute(anyString());

        // 예외가 전파되지 않아야 함 (기동 중단 방지)
        initializer.ensureFulltextIndex();

        // 최초 확인 + catch 내 재확인 = queryForObject 2회
        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(Integer.class), any());
    }

    @Test
    @DisplayName("생성 실패 후 재확인(indexExists)도 실패해도 예외를 전파하지 않는다")
    void swallowsExceptionWhenRecheckAlsoFails() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
            .thenReturn(0)                              // 최초 확인: 없음
            .thenThrow(new RuntimeException("DB 끊김")); // catch 내 재확인도 실패
        doThrow(new RuntimeException("DB 오류")).when(jdbcTemplate).execute(anyString());

        // 예외가 전파되지 않아야 함 (기동 비차단 의도 유지)
        initializer.ensureFulltextIndex();
    }
}
