package com.sparta.one_stop.dummy.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DummyProductSourceGroupRepository extends JpaRepository<DummyProductSourceGroup, Long> {

    // 재실행 시 기존 기본 상품(그룹) 매칭 — 있으면 같은 Product에 변형 누적
    Optional<DummyProductSourceGroup> findBySourceAndBaseSourceKey(String source, String baseSourceKey);

    // 신규 그룹 여부 — 이미지를 신규 생성 때만 받기 위함(재실행 orphan 방지)
    boolean existsBySourceAndBaseSourceKey(String source, String baseSourceKey);
}
