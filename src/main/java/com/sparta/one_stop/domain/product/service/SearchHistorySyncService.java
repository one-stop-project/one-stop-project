package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.event.SearchHistoryEvent;
import com.sparta.one_stop.domain.product.entity.SearchHistory;
import com.sparta.one_stop.domain.product.repository.SearchHistoryRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Redis 큐에 쌓인 검색 로그를 모아서 DB에 한 번에 저장
// 저장(커밋) 성공 후에만 호출자가 큐를 비운다.
// ack(LTRIM) 실패로 같은 batch가 재처리돼도 eventId로 중복 INSERT를 거른다(멱등).
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchHistorySyncService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void syncBatch(List<SearchHistoryEvent> events) {
        if (events.isEmpty()) return;

        // 1) eventId 기준으로 batch 내부 중복 제거.
        //    eventId가 없는 건 배포 직전 큐에 남아있던 레거시 payload뿐인데(새 적재는 항상 eventId 부여),
        //    멱등키가 없어 재처리 시 중복을 막을 수 없으므로 여기서 버린다(검색기록 부가데이터라 유실 무해).
        Map<String, SearchHistoryEvent> deduped = new LinkedHashMap<>();
        for (SearchHistoryEvent e : events) {
            if (e.eventId() == null) {
                log.warn("[SearchHistory] eventId 없는 레거시 이벤트 드롭 (keyword={})", e.keyword());
                continue;
            }
            deduped.putIfAbsent(e.eventId(), e);
        }
        if (deduped.isEmpty()) return;

        // 2) 이미 저장된 eventId 제외 (ack 실패로 같은 batch가 재처리될 때 중복 INSERT 방지)
        Set<String> persisted = new HashSet<>(searchHistoryRepository.findExistingEventIds(deduped.keySet()));

        List<SearchHistory> rows = new ArrayList<>(deduped.size());
        for (SearchHistoryEvent e : deduped.values()) {
            if (persisted.contains(e.eventId())) {
                continue; // 이미 저장된 이벤트는 건너뛴다
            }
            rows.add(SearchHistory.builder()
                .eventId(e.eventId())
                .keyword(e.keyword())
                .user(resolveUserRef(e.userId()))
                .searchedAt(e.searchedAt())
                .build());
        }

        if (!rows.isEmpty()) {
            searchHistoryRepository.saveAll(rows);
        }
    }

    // 탈퇴 유저는 user=null로 저장 (로그 유실 방지) — 기존 정책 유지
    private User resolveUserRef(Long userId) {
        if (userId == null) return null;
        try {
            return userRepository.getReferenceById(userId);
        } catch (EntityNotFoundException ex) {
            log.warn("[SearchHistory] missing user (userId={}), saving as anonymous", userId);
            return null;
        }
    }
}
