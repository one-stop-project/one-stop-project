package com.sparta.one_stop.global.outbox.repository;

import com.sparta.one_stop.global.enums.outbox.OutboxEventStatus;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 이벤트 고유 ID로 Outbox 이벤트 조회
    Optional<OutboxEvent> findByEventId(String eventId);

    // PENDING 상태 이벤트를 created_at 오름차순으로 조회 (폴링용)
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
        OutboxEventStatus status,
        Pageable pageable
    );

    // PENDING → PROCESSING 조건부 선점 (affected row 1이면 성공, 0이면 이미 선점됨)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEvent e
        set e.status = :newStatus
        where e.id = :id
          and e.status = :currentStatus
    """)
    int updateStatusByIdAndStatus(
        @Param("id") Long id,
        @Param("currentStatus") OutboxEventStatus currentStatus,
        @Param("newStatus") OutboxEventStatus newStatus
    );

}
