package com.sparta.one_stop.global.outbox.repository;

import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 이벤트 고유 ID로 Outbox 이벤트 조회
    // 중복 이벤트 저장 여부 확인이나 멱등성 보장에 사용한다
    Optional<OutboxEvent> findByEventId(String eventId);

}
