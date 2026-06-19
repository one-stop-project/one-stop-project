package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
import com.sparta.one_stop.domain.coupon.service.CouponCommandService;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderCancelHistory;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderCancelHistoryRepository;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
import com.sparta.one_stop.global.enums.order.CancelActorType;
import com.sparta.one_stop.global.enums.order.OrderCancelType;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSellerService {

    private static final String SELLER_SUSPEND_CANCEL_REASON = "판매자 계정 정지로 인한 자동 취소";
    private static final int NOT_RESTORED_POINT = 0;

    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final AdminActionHistoryRepository adminActionHistoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCancelHistoryRepository orderCancelHistoryRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final UserStatusCacheService userStatusCacheService;
    private final PointService pointService;
    private final CouponCommandService couponCommandService;

    // 대기 중인 판매자 목록 조회
    public List<Seller> getPendingSellers() {
        return sellerRepository.findAllByStatus(SellerStatus.PENDING);
    }

    // 판매자 승인
    @Transactional
    public void approveSeller(Long sellerId, Long actorId) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() != SellerStatus.PENDING) {
            throw new CustomException(ErrorCode.ADMIN_018);
        }

        seller.approve();

        // 승인 이력 저장 (reason 없음)
        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.SELLER)
            .targetId(sellerId)
            .action(AdminActionType.APPROVE)
            .build());
    }

    // 판매자 반려
    @Transactional
    public void rejectSeller(Long sellerId, Long actorId, String reason) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() != SellerStatus.PENDING) {
            throw new CustomException(ErrorCode.ADMIN_018);
        }

        seller.reject();

        // 반려 이력 저장 (reason 필수)
        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.SELLER)
            .targetId(sellerId)
            .action(AdminActionType.REJECT)
            .reason(reason)
            .build());
    }

    // 판매자 강제 비활성화
    @Transactional
    public void forceInactiveSeller(Long sellerId, Long actorId, String reason) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() == SellerStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.ADMIN_019);
        }

        seller.getUser().suspend();
        seller.suspend();
        productRepository.updateStatusBySellerId(seller.getId(), ProductStatus.FORCE_INACTIVE);

        cancelActiveOrdersBySeller(sellerId, actorId);

        // tokenVersion++ (DB 영속 무효화)
        seller.getUser().increaseTokenVersion();

        // 캐시 무효화 — 정지 즉시 인증 차단 (캐시 ACTIVE 잔존 우회 방지)
        // tokenVersion 캐시는 커밋 후 Listener가 evict
        Long userId = seller.getUser().getId();
        userStatusCacheService.evict(userId);

        // 전기기 로그아웃 — RT 삭제 + AT 무효화 (정지 판매자 RT 무기한 재발급 차단)
        eventPublisher.publishEvent(
            new AllDevicesLogoutEvent(userId, "SUSPENDED"));

        // 강제비활성화 이력 저장 (reason 필수)
        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.SELLER)
            .targetId(sellerId)
            .action(AdminActionType.FORCE_INACTIVE)
            .reason(reason)
            .build());
    }

    // 판매자 정지 해제
    @Transactional
    public void reactivateSeller(Long sellerId, Long actorId) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() != SellerStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.ADMIN_010);
        }

        seller.reactivate();
        seller.getUser().reactivate();

        Long userId = seller.getUser().getId();
        userStatusCacheService.evict(userId);

        // 정지 해제 이력 저장 (상품은 정책상 자동 복구하지 않음)
        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.SELLER)
            .targetId(sellerId)
            .action(AdminActionType.REACTIVATE)
            .build());
    }

    // 판매자 정지 시 ORDERED/CONFIRMED 상태 주문 자동 취소 및 재고 복구
    // 주문의 모든 OrderItem이 CANCELLED가 된 경우에만 주문 단위 혜택을 한 번 복구한다
    // 일부 OrderItem만 취소된 부분 취소 주문은 포인트·쿠폰을 복구하지 않는다.
    private void cancelActiveOrdersBySeller(Long sellerId, Long actorId) {
        List<OrderItem> activeItems = orderItemRepository.findBySellerIdAndStatusIn(
            sellerId,
            List.of(OrderItemStatus.ORDERED, OrderItemStatus.CONFIRMED)
        );

        Map<Long, List<OrderItem>> itemsByOrderId = activeItems.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                item -> item.getOrder().getId(),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));

        for (List<OrderItem> sellerOrderItems : itemsByOrderId.values()) {
            Order order = sellerOrderItems.get(0).getOrder();

            for (OrderItem orderItem : sellerOrderItems) {
                orderItem.getProductItem().increaseStock(orderItem.getQuantity());
                orderItem.cancel();
            }

            int restoredPoint = restoreBenefitsWhenFullyCancelled(order);
            saveCancelHistories(sellerOrderItems, actorId, restoredPoint);
        }
    }

    /**
     * 주문 전체가 취소된 경우에만 주문 단위 포인트와 쿠폰을 복구한다.
     * 이미 CANCELLED인 주문은 재처리하지 않아 중복 복구를 방지한다.
     */
    private int restoreBenefitsWhenFullyCancelled(Order order) {
        List<OrderItem> allOrderItems = orderItemRepository.findAllByOrderId(order.getId());

        boolean fullyCancelled = !allOrderItems.isEmpty()
            && allOrderItems.stream()
            .allMatch(item -> item.getStatus() == OrderItemStatus.CANCELLED);

        if (!fullyCancelled || order.getStatus() == OrderStatus.CANCELLED) {
            return NOT_RESTORED_POINT;
        }

        couponCommandService.restoreCouponByOrder(order);
        order.cancel();
        return pointService.refundPointByOrder(order);
    }

    /**
     * 취소 이력은 기존처럼 OrderItem 단위로 남긴다.
     * 주문 단위 포인트 복구액은 중복 합산을 피하기 위해 첫 번째 이력에만 기록한다.
     */
    private void saveCancelHistories(
        List<OrderItem> cancelledItems,
        Long actorId,
        int restoredPoint
    ) {
        for (int index = 0; index < cancelledItems.size(); index++) {
            OrderItem orderItem = cancelledItems.get(index);
            int historyRestoredPoint = index == 0 ? restoredPoint : NOT_RESTORED_POINT;

            orderCancelHistoryRepository.save(new OrderCancelHistory(
                orderItem.getOrder(),
                orderItem,
                CancelActorType.ADMIN,
                actorId,
                OrderCancelType.ADMIN_CANCEL,
                SELLER_SUSPEND_CANCEL_REASON,
                orderItem.getPrice() * orderItem.getQuantity(),
                historyRestoredPoint
            ));
        }
    }
}
