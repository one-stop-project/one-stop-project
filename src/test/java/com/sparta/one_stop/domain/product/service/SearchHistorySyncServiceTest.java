package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.one_stop.domain.product.entity.SearchHistory;
import com.sparta.one_stop.domain.product.event.SearchHistoryEvent;
import com.sparta.one_stop.domain.product.repository.SearchHistoryRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchHistorySyncService - Redis 큐 raw 이벤트를 batch INSERT")
class SearchHistorySyncServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long MISSING_USER_ID = 999L;

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SearchHistorySyncService syncService;

    @Test
    @DisplayName("빈 이벤트 리스트 → repository 호출 0")
    void syncBatch_empty_noRepositoryCall() {
        // when
        syncService.syncBatch(List.of());

        // then
        verifyNoInteractions(searchHistoryRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("정상 로그인 유저 → getReferenceById 호출 + user 설정된 SearchHistory 저장")
    void syncBatch_loggedInUser_attachesUserReference() {
        // given
        User userRef = newUser(USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(userRef);

        SearchHistoryEvent event = new SearchHistoryEvent(
            "macbook", USER_ID, LocalDateTime.of(2026, 5, 29, 14, 0, 0));

        // when
        syncService.syncBatch(List.of(event));

        // then
        ArgumentCaptor<List<SearchHistory>> captor = ArgumentCaptor.forClass(List.class);
        then(searchHistoryRepository).should().saveAll(captor.capture());

        List<SearchHistory> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getKeyword()).isEqualTo("macbook");
        assertThat(saved.get(0).getUser()).isSameAs(userRef);
    }

    @Test
    @DisplayName("비로그인(userId=null) → userRepository 호출 0 + user=null로 저장")
    void syncBatch_anonymous_userIsNull() {
        // given
        SearchHistoryEvent event = new SearchHistoryEvent(
            "airpods", null, LocalDateTime.of(2026, 5, 29, 14, 5, 0));

        // when
        syncService.syncBatch(List.of(event));

        // then
        then(userRepository).should(never()).getReferenceById(eq(MISSING_USER_ID));
        verifyNoInteractions(userRepository);

        ArgumentCaptor<List<SearchHistory>> captor = ArgumentCaptor.forClass(List.class);
        then(searchHistoryRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getUser()).isNull();
    }

    @Test
    @DisplayName("탈퇴/삭제된 유저 (EntityNotFoundException) → user=null 폴백, INSERT는 계속")
    void syncBatch_missingUser_fallsBackToNull() {
        // given
        given(userRepository.getReferenceById(MISSING_USER_ID))
            .willThrow(new EntityNotFoundException("user not found"));

        SearchHistoryEvent event = new SearchHistoryEvent(
            "ipad", MISSING_USER_ID, LocalDateTime.of(2026, 5, 29, 14, 10, 0));

        // when
        syncService.syncBatch(List.of(event));

        // then
        ArgumentCaptor<List<SearchHistory>> captor = ArgumentCaptor.forClass(List.class);
        then(searchHistoryRepository).should().saveAll(captor.capture());

        List<SearchHistory> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getKeyword()).isEqualTo("ipad");
        assertThat(saved.get(0).getUser()).isNull();
    }

    @Test
    @DisplayName("event.searchedAt이 5분 지연 후 INSERT 시점에도 raw 시각 그대로 보존")
    void syncBatch_preservesSearchedAt() {
        // given
        LocalDateTime rawSearchedAt = LocalDateTime.of(2026, 5, 29, 13, 55, 30);
        SearchHistoryEvent event = new SearchHistoryEvent(
            "ssd", null, rawSearchedAt);

        // when
        syncService.syncBatch(List.of(event));

        // then
        ArgumentCaptor<List<SearchHistory>> captor = ArgumentCaptor.forClass(List.class);
        then(searchHistoryRepository).should().saveAll(captor.capture());

        // sync 시점(나중)이 아니라 event 발생 시점이 영속화돼야 함
        assertThat(captor.getValue().get(0).getSearchedAt()).isEqualTo(rawSearchedAt);
    }

    @Test
    @DisplayName("로그인 + 비로그인 + 미존재 유저가 섞인 batch → 모두 한 번에 saveAll")
    void syncBatch_mixedBatch_oneSaveAllCall() {
        // given
        User userRef = newUser(USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(userRef);
        given(userRepository.getReferenceById(MISSING_USER_ID))
            .willThrow(new EntityNotFoundException("user not found"));

        List<SearchHistoryEvent> events = List.of(
            new SearchHistoryEvent("a1", USER_ID, LocalDateTime.now()),
            new SearchHistoryEvent("a2", null, LocalDateTime.now()),
            new SearchHistoryEvent("a3", MISSING_USER_ID, LocalDateTime.now())
        );

        // when
        syncService.syncBatch(events);

        // then
        ArgumentCaptor<List<SearchHistory>> captor = ArgumentCaptor.forClass(List.class);
        then(searchHistoryRepository).should().saveAll(captor.capture());

        List<SearchHistory> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getUser()).isSameAs(userRef);
        assertThat(saved.get(1).getUser()).isNull();
        assertThat(saved.get(2).getUser()).isNull();
    }

    private User newUser(Long id) {
        User user = User.builder()
            .email("user" + id + "@test.com")
            .password("password")
            .name("user" + id)
            .role(UserRole.BUYER)
            .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
