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
import java.util.List;

// Redis LIST에 누적된 raw 검색 이벤트를 batch로 DB INSERT
// Scheduler가 호출 — 트랜잭션 커밋 성공 후에만 ack(LTRIM)
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchHistorySyncService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void syncBatch(List<SearchHistoryEvent> events) {
        if (events.isEmpty()) return;

        List<SearchHistory> rows = new ArrayList<>(events.size());
        for (SearchHistoryEvent e : events) {
            User userRef = null;
            if (e.userId() != null) {
                try {
                    userRef = userRepository.getReferenceById(e.userId());
                } catch (EntityNotFoundException ex) {
                    // 탈퇴/삭제된 유저는 user_id=null 로 저장 — raw 로그 유실 방지
                    log.warn("[SearchHistory] missing user (userId={}), saving as anonymous", e.userId());
                }
            }
            rows.add(SearchHistory.builder()
                .keyword(e.keyword())
                .user(userRef)
                .searchedAt(e.searchedAt())
                .build());
        }
        searchHistoryRepository.saveAll(rows);
    }
}
