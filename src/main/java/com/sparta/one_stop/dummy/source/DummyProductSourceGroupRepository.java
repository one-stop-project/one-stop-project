package com.sparta.one_stop.dummy.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DummyProductSourceGroupRepository extends JpaRepository<DummyProductSourceGroup, Long> {

    // 재실행 시 기존 기본 상품(그룹) 매칭 — 있으면 같은 Product에 변형 누적
    Optional<DummyProductSourceGroup> findBySourceAndBaseSourceKey(String source, String baseSourceKey);
}
