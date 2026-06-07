package com.sparta.one_stop.domain.review.event;

/**
 * 리뷰 수정/삭제 시 AI 요약 전체 갱신을 트리거하는 이벤트.
 * 리뷰 내용이나 평점이 바뀌면 증분 업데이트가 아닌 전체 재요약이 필요합니다.
 */
public record ReviewSummaryRefreshEvent(
    Long productId
) {}
