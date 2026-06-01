package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.response.PopularProductResponse;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.product.repository.SalesWeightedRow;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularProductService {

    // Redis 키
    public static final String VIEW_BUCKET_PREFIX = "popular:view:bucket:1h:";
    private static final String POPULAR_KEY = "popular:products";
    private static final String POPULAR_BUILDING_KEY = "popular:products:building";
    private static final String VIEW_WEIGHTED_KEY = "popular:view:weighted:tmp";
    private static final String SALES_WEIGHTED_KEY = "popular:sales:weighted:tmp";

    private static final int TOP_N = 20;
    private static final long ZSET_TTL_SECONDS = 600L;            // 10분 — 5분 갱신보다 길게 잡아 안전 마진
    public static final long VIEW_BUCKET_TTL_SECONDS = 4 * 3600L; // 4시간 = 3시간 윈도우 + 안전 마진 1시간

    // 조회수 vs 판매수 영향력 비율
    private static final double VIEW_WEIGHT = 0.7;
    private static final double SALES_WEIGHT = 0.3;

    // 시간 구간 가중치 (오래된 → 최근). 최근일수록 큰 영향
    private static final double WEIGHT_OLDEST = 0.1;
    private static final double WEIGHT_MIDDLE = 0.3;
    private static final double WEIGHT_RECENT = 0.6;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    // 판매수 카운트 대상 상태 (결제완료 이상, 취소/거절 제외)
    private static final List<OrderItemStatus> SALES_COUNT_STATUSES = List.of(
        OrderItemStatus.ORDERED,
        OrderItemStatus.CONFIRMED,
        OrderItemStatus.SHIPPING,
        OrderItemStatus.DELIVERED
    );

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;

    // 인기 상품 랭킹 갱신 — 3단계:
    //   1) 최근 3시간 조회수를 시간 가중치로 합산
    //   2) 최근 3일 판매수를 시간 가중치로 합산
    //   3) 둘을 0.7 / 0.3 비율로 합쳐 상위 N개만 남기고 노출용 키로 교체
    // Redis 예외 시 catch — 다음 주기에 자동 재시도
    public void refresh() {
        LocalDateTime now = LocalDateTime.now(KST);
        try {
            buildViewWeighted(now);
            buildSalesWeighted(now);
            mergeAndRename();
            log.info("[Popular] refresh done");
        } catch (Exception e) {
            log.error("[Popular] refresh failed", e);
        }
    }

    // 인기 상품 TOP N 응답 (Redis 장애 시 DB fallback)
    public List<PopularProductResponse> getPopular(int limit) {
        try {
            Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(POPULAR_KEY, 0, (long) limit * 2 - 1);

            if (tuples == null || tuples.isEmpty()) {
                return dbFallback(limit);
            }

            List<Long> productIds = tuples.stream()
                .map(t -> Long.parseLong(t.getValue()))
                .toList();

            return buildResponse(productIds, limit);
        } catch (Exception e) {
            log.warn("[Popular] redis fetch failed, falling back to DB", e);
            return dbFallback(limit);
        }
    }

    // 인기 상품 ID 리스트 (정렬 순서 활용용)
    public List<Long> getPopularProductIds(int limit) {
        try {
            Set<String> ids = redisTemplate.opsForZSet().reverseRange(POPULAR_KEY, 0, limit - 1L);
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream().map(Long::parseLong).toList();
        } catch (Exception e) {
            log.warn("[Popular] redis range failed", e);
            return List.of();
        }
    }

    public static String viewBucketKey(LocalDateTime kstTime) {
        return VIEW_BUCKET_PREFIX + kstTime.format(HOUR_FMT);
    }

    // ===== 내부 구현 =====

    // 최근 3시간(현재/-1h/-2h) 조회수를 시간 가중치로 합산
    private void buildViewWeighted(LocalDateTime now) {
        String oldest = viewBucketKey(now.minusHours(2));
        String middle = viewBucketKey(now.minusHours(1));
        String recent = viewBucketKey(now);

        redisTemplate.opsForZSet().unionAndStore(
            oldest,
            List.of(middle, recent),
            VIEW_WEIGHTED_KEY,
            Aggregate.SUM,
            Weights.of(WEIGHT_OLDEST, WEIGHT_MIDDLE, WEIGHT_RECENT)
        );
    }

    // 최근 3일 판매수를 일별 가중치로 합산해 점수화
    private void buildSalesWeighted(LocalDateTime now) {
        LocalDateTime recentSince = now.minusDays(1);
        LocalDateTime middleSince = now.minusDays(2);
        LocalDateTime oldestSince = now.minusDays(3);

        List<SalesWeightedRow> rows = productRepository.aggregateSalesByTimeWindow(
            recentSince, middleSince, oldestSince, SALES_COUNT_STATUSES
        );

        redisTemplate.delete(SALES_WEIGHTED_KEY);

        for (SalesWeightedRow row : rows) {
            double score = nullSafe(row.getRecent()) * WEIGHT_RECENT
                         + nullSafe(row.getMiddle()) * WEIGHT_MIDDLE
                         + nullSafe(row.getOldest()) * WEIGHT_OLDEST;
            if (score > 0) {
                redisTemplate.opsForZSet().add(
                    SALES_WEIGHTED_KEY, row.getProductId().toString(), score);
            }
        }
    }

    // 조회 점수 + 판매 점수를 0.7/0.3 비율로 합산 → 상위 N개 → 노출용 키로 교체
    private void mergeAndRename() {
        redisTemplate.opsForZSet().unionAndStore(
            VIEW_WEIGHTED_KEY,
            List.of(SALES_WEIGHTED_KEY),
            POPULAR_BUILDING_KEY,
            Aggregate.SUM,
            Weights.of(VIEW_WEIGHT, SALES_WEIGHT)
        );

        // 합칠 점수가 하나도 없으면 결과 키가 안 생김
        // → 기존 랭킹을 비워서 DB 조회 경로로 떨어뜨림
        Boolean exists = redisTemplate.hasKey(POPULAR_BUILDING_KEY);
        if (!Boolean.TRUE.equals(exists)) {
            redisTemplate.delete(POPULAR_KEY);
            return;
        }

        // TOP N 만 남기고 하위 점수 제거
        redisTemplate.opsForZSet().removeRange(POPULAR_BUILDING_KEY, 0, -TOP_N - 1L);

        redisTemplate.rename(POPULAR_BUILDING_KEY, POPULAR_KEY);
        redisTemplate.expire(POPULAR_KEY, Duration.ofSeconds(ZSET_TTL_SECONDS));
    }

    private List<PopularProductResponse> dbFallback(int limit) {
        Pageable pageable = PageRequest.of(0, limit * 2,
            Sort.by(Sort.Direction.DESC, "salesCount"));
        Page<Product> page = productRepository.findApproved(
            ProductStatus.APPROVED, SellerStatus.APPROVED, pageable);

        if (page.isEmpty()) {
            return List.of();
        }

        List<Long> ids = page.stream().map(Product::getId).toList();
        return buildResponse(ids, limit);
    }

    private List<PopularProductResponse> buildResponse(List<Long> productIds, int limit) {
        Map<Long, Product> productMap = productRepository.findAllByIdsWithItems(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<PopularProductResponse> result = new ArrayList<>();
        AtomicInteger rank = new AtomicInteger(1);
        for (Long pid : productIds) {
            Product p = productMap.get(pid);
            if (p == null) continue;
            if (!isVisibleAndOnSale(p)) continue;
            result.add(PopularProductResponse.from(rank.getAndIncrement(), p));
            if (result.size() >= limit) break;
        }
        return result;
    }

    private boolean isVisibleAndOnSale(Product p) {
        if (!p.isApproved()) return false;
        if (p.getSeller().getStatus() != SellerStatus.APPROVED) return false;
        return p.getProductItems().stream().anyMatch(ProductItem::isOnSale);
    }

    private long nullSafe(Long v) {
        return v == null ? 0L : v;
    }
}
