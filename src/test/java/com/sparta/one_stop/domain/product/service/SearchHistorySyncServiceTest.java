package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchHistorySyncService - Redis 큐 raw 이벤트를 멱등 batch INSERT")
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
        syncService.syncBatch(List.of());

        verifyNoInteractions(searchHistoryRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("정상 로그인 유저 → getReferenceById 호출 + user/eventId 설정된 SearchHistory 저장")
    void syncBatch_loggedInUser_attachesUserReference() {
        User userRef = newUser(USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(userRef);
        noneAlreadyPersisted();

        syncService.syncBatch(List.of(evt("macbook", USER_ID, LocalDateTime.of(2026, 5, 29, 14, 0, 0))));

        List<SearchHistory> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getKeyword()).isEqualTo("macbook");
        assertThat(saved.get(0).getUser()).isSameAs(userRef);
        assertThat(saved.get(0).getEventId()).isNotBlank();
    }

    @Test
    @DisplayName("비로그인(userId=null) → userRepository 호출 0 + user=null로 저장")
    void syncBatch_anonymous_userIsNull() {
        noneAlreadyPersisted();

        syncService.syncBatch(List.of(evt("airpods", null, LocalDateTime.of(2026, 5, 29, 14, 5, 0))));

        verifyNoInteractions(userRepository);
        assertThat(captureSaved().get(0).getUser()).isNull();
    }

    @Test
    @DisplayName("탈퇴/삭제된 유저 (EntityNotFoundException) → user=null 폴백, INSERT는 계속")
    void syncBatch_missingUser_fallsBackToNull() {
        given(userRepository.getReferenceById(MISSING_USER_ID))
            .willThrow(new EntityNotFoundException("user not found"));
        noneAlreadyPersisted();

        syncService.syncBatch(List.of(evt("ipad", MISSING_USER_ID, LocalDateTime.of(2026, 5, 29, 14, 10, 0))));

        List<SearchHistory> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getKeyword()).isEqualTo("ipad");
        assertThat(saved.get(0).getUser()).isNull();
    }

    @Test
    @DisplayName("event.searchedAt이 5분 지연 후 INSERT 시점에도 raw 시각 그대로 보존")
    void syncBatch_preservesSearchedAt() {
        LocalDateTime rawSearchedAt = LocalDateTime.of(2026, 5, 29, 13, 55, 30);
        noneAlreadyPersisted();

        syncService.syncBatch(List.of(evt("ssd", null, rawSearchedAt)));

        assertThat(captureSaved().get(0).getSearchedAt()).isEqualTo(rawSearchedAt);
    }

    @Test
    @DisplayName("로그인 + 비로그인 + 미존재 유저가 섞인 batch → 모두 한 번에 saveAll")
    void syncBatch_mixedBatch_oneSaveAllCall() {
        User userRef = newUser(USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(userRef);
        given(userRepository.getReferenceById(MISSING_USER_ID))
            .willThrow(new EntityNotFoundException("user not found"));
        noneAlreadyPersisted();

        syncService.syncBatch(List.of(
            evt("a1", USER_ID, LocalDateTime.now()),
            evt("a2", null, LocalDateTime.now()),
            evt("a3", MISSING_USER_ID, LocalDateTime.now())
        ));

        List<SearchHistory> saved = captureSaved();
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getUser()).isSameAs(userRef);
        assertThat(saved.get(1).getUser()).isNull();
        assertThat(saved.get(2).getUser()).isNull();
    }

    // ===== 멱등(#363) =====

    @Test
    @DisplayName("이미 저장된 eventId만 있는 batch(ack 실패 후 재처리) → saveAll 호출 안 함(중복 0)")
    void syncBatch_allAlreadyPersisted_noInsert() {
        given(searchHistoryRepository.findExistingEventIds(anyCollection()))
            .willReturn(List.of("e-dup"));

        syncService.syncBatch(List.of(
            new SearchHistoryEvent("e-dup", "macbook", null, LocalDateTime.now())));

        then(searchHistoryRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("일부만 이미 저장된 batch → 미저장 eventId만 INSERT")
    void syncBatch_partiallyPersisted_insertsOnlyMissing() {
        given(searchHistoryRepository.findExistingEventIds(anyCollection()))
            .willReturn(List.of("e-1"));   // e-1은 이미 저장됨

        syncService.syncBatch(List.of(
            new SearchHistoryEvent("e-1", "old", null, LocalDateTime.now()),
            new SearchHistoryEvent("e-2", "new", null, LocalDateTime.now())));

        List<SearchHistory> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getEventId()).isEqualTo("e-2");
        assertThat(saved.get(0).getKeyword()).isEqualTo("new");
    }

    @Test
    @DisplayName("같은 batch 안에 동일 eventId가 여러 번 → DB에는 1건만 전달")
    void syncBatch_duplicateInsideBatch_savedOnce() {
        noneAlreadyPersisted();

        syncService.syncBatch(List.of(
            new SearchHistoryEvent("e-same", "macbook", null, LocalDateTime.now()),
            new SearchHistoryEvent("e-same", "macbook", null, LocalDateTime.now())));

        List<SearchHistory> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getEventId()).isEqualTo("e-same");
    }

    // ===== 헬퍼 =====

    private void noneAlreadyPersisted() {
        given(searchHistoryRepository.findExistingEventIds(anyCollection())).willReturn(List.of());
    }

    private List<SearchHistory> captureSaved() {
        ArgumentCaptor<List<SearchHistory>> captor = ArgumentCaptor.forClass(List.class);
        then(searchHistoryRepository).should().saveAll(captor.capture());
        return captor.getValue();
    }

    // eventId는 적재 시점에 부여되므로 테스트에선 임의 UUID로 채운다 (중복 검증이 필요한 케이스는 직접 명시)
    private SearchHistoryEvent evt(String keyword, Long userId, LocalDateTime at) {
        return new SearchHistoryEvent(UUID.randomUUID().toString(), keyword, userId, at);
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
