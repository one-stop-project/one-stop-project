package com.sparta.one_stop.dummy.product;

// 더미 상품 저장 결과 (시드 실행 통계용)
public enum DummyWriteResult {
    CREATED,   // 신규 그룹 → Product 생성
    UPDATED    // 기존 그룹 → 가격 갱신 / 신규 변형 추가
}
