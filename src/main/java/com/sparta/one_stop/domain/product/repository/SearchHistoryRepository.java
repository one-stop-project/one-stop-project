package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    // 이미 저장된 eventId만 추려 반환 — syncBatch 재처리(ack 실패 후 재peek) 시 중복 INSERT를 거르는 데 사용
    @Query("select s.eventId from SearchHistory s where s.eventId in :eventIds")
    List<String> findExistingEventIds(@Param("eventIds") Collection<String> eventIds);
}
