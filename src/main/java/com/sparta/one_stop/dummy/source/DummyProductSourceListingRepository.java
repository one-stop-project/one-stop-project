package com.sparta.one_stop.dummy.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DummyProductSourceListingRepository extends JpaRepository<DummyProductSourceListing, Long> {

    // 재실행 시 개별 변형 매칭 — 있으면 가격만 갱신
    Optional<DummyProductSourceListing> findBySourceAndListingSourceKey(String source, String listingSourceKey);

    // 한 그룹에 속한 기존 변형 전체 (신규 변형 추가 / 사라진 변형 stale 판단용)
    // source 포함 — 마켓 추가 시 baseSourceKey 충돌로 교차 오염되지 않게
    List<DummyProductSourceListing> findAllBySourceAndBaseSourceKey(String source, String baseSourceKey);
}
