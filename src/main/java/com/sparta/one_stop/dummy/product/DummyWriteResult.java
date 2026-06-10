package com.sparta.one_stop.dummy.product;

// 더미 상품 저장 결과 (시드 실행 통계용)
public enum DummyWriteResult {
    CREATED,   // 신규 그룹 → Product 생성
    UPDATED,   // 기존 그룹 → 가격 갱신 / 신규 변형 추가
    SKIPPED    // 매핑은 있으나 상품이 삭제됨(stale) → 실제 쓰기 없이 건너뜀
}
