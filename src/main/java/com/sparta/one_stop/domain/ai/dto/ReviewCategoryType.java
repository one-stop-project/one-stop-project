package com.sparta.one_stop.domain.ai.dto;

/**
 * AI 리뷰 요약 시 프롬프트를 선택하는 카테고리 타입.
 * 호출 측에서 DB Category 이름을 기반으로 매핑해서 전달합니다.
 */
public enum ReviewCategoryType {
    CLOTHING,    // 의류 – 착용감/사이즈/소재
    ELECTRONICS, // 전자제품 – 성능/배터리/호환성
    FOOD,        // 식품 – 맛/식감/신선도
    GENERAL      // 기타/미분류
}
