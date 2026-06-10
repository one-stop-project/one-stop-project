package com.sparta.one_stop.domain.product.support;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// 인기 집계(인기상품·인기검색어·조회수)의 공통 시간 기준.
// 여러 서비스에 복붙돼 있던 타임존·시간버킷 포맷·시간윈도우 가중치를 한곳에 모은다.
public final class PopularityWindow {

    // 집계 버킷·now 계산의 기준 타임존
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 1시간 버킷 키 포맷(yyyyMMddHH). DateTimeFormatter는 불변이라 공유해도 안전
    public static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    // 최근 3시간 시간윈도우 가중치(오래된 → 최근, 최근일수록 큰 영향).
    // 인기상품·인기검색어가 공유하는 값 — 한쪽만 따로 튜닝하려면 이 상수를 분리해야 함
    public static final double WEIGHT_OLDEST = 0.1;
    public static final double WEIGHT_MIDDLE = 0.3;
    public static final double WEIGHT_RECENT = 0.6;
    private PopularityWindow() {
    }
}
