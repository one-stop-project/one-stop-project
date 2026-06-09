package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
import com.sparta.one_stop.domain.order.entity.OrderCancelHistory;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderCancelHistoryRepository;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
import com.sparta.one_stop.global.enums.order.CancelActorType;
import com.sparta.one_stop.global.enums.order.OrderCancelType;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    // 대기 중인 판매자 목록 조회
    public List<Seller> getPendingSellers() {
        return sellerRepository.findAllByStatus(SellerStatus.PENDING);
    }

    // 판매자 승인
    @Transactional
    public void approveSeller(Long sellerId, Long actorId) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() == SellerStatus.APPROVED) {
            throw new CustomException(ErrorCode.ADMIN_002);
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

        if (seller.getStatus() == SellerStatus.REJECTED) {
            throw new CustomException(ErrorCode.ADMIN_003);
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

        if (seller.getUser().isSuspended()) {
            throw new CustomException(ErrorCode.ADMIN_004);
        }

        seller.getUser().suspend();
        seller.suspend();
        productRepository.updateStatusBySellerId(seller.getId(), ProductStatus.FORCE_INACTIVE);

        cancelActiveOrdersBySeller(sellerId, actorId);

        // 캐시 무효화 — 정지 즉시 인증 차단 (캐시 ACTIVE 잔존 우회 방지)
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
    private void cancelActiveOrdersBySeller(Long sellerId, Long actorId) {
        List<OrderItem> activeItems = orderItemRepository.findBySellerIdAndStatusIn(
            sellerId,
            List.of(OrderItemStatus.ORDERED, OrderItemStatus.CONFIRMED)
        );

        for (OrderItem orderItem : activeItems) {
            orderItem.getProductItem().increaseStock(orderItem.getQuantity());
            orderItem.cancel();

            orderCancelHistoryRepository.save(new OrderCancelHistory(
                orderItem.getOrder(),
                orderItem,
                CancelActorType.ADMIN,
                actorId,
                OrderCancelType.ADMIN_CANCEL,
                SELLER_SUSPEND_CANCEL_REASON,
                orderItem.getPrice() * orderItem.getQuantity(),
                NOT_RESTORED_POINT
            ));
        }
    }
}
