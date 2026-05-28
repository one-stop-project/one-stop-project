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
    private static final long ZSET_TTL_SECONDS = 600L;
    public static final long VIEW_BUCKET_TTL_SECONDS = 4 * 3600L;

    // 점수 가중치
    private static final double VIEW_WEIGHT = 0.7;
    private static final double SALES_WEIGHT = 0.3;

    // 시간 구간 가중치 (오래된 → 최근)
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

    // 인기 상품 ZSET 갱신 (Redis 예외는 다음 주기 재시도)
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

    private void mergeAndRename() {
        redisTemplate.opsForZSet().unionAndStore(
            VIEW_WEIGHTED_KEY,
            List.of(SALES_WEIGHTED_KEY),
            POPULAR_BUILDING_KEY,
            Aggregate.SUM,
            Weights.of(VIEW_WEIGHT, SALES_WEIGHT)
        );

        // TOP N 만 남기고 하위 점수 제거
        redisTemplate.opsForZSet().removeRange(POPULAR_BUILDING_KEY, 0, -TOP_N - 1L);

        // atomic 교체
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
