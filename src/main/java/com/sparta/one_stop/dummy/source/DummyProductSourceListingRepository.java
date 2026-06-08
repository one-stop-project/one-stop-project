package com.sparta.one_stop.dummy.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DummyProductSourceListingRepository extends JpaRepository<DummyProductSourceListing, Long> {

    // 재실행 시 개별 변형 매칭 — 있으면 가격만 갱신
    Optional<DummyProductSourceListing> findBySourceAndListingSourceKey(String source, String listingSourceKey);

    // 한 그룹에 속한 기존 변형 전체 (신규 변형 추가 / 사라진 변형 stale 판단용)
    List<DummyProductSourceListing> findAllByBaseSourceKey(String baseSourceKey);
}
