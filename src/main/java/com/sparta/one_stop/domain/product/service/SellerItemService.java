package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.request.InboundRequest;
import com.sparta.one_stop.domain.product.dto.request.ItemUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.InboundResponse;
import com.sparta.one_stop.domain.product.dto.response.ItemUpdateResponse;
import com.sparta.one_stop.domain.product.entity.InventoryHistory;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.InventoryHistoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.global.enums.product.InventoryHistoryType;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerItemService {

    // 옵션당 재고 상한
    private static final long MAX_STOCK = 99_999L; // 임시 재고 상한 수정 가능

    private final ProductItemRepository productItemRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;

    // productId를 옵션 조회 후에야 알 수 있어 @CacheEvict 어노테이션으로는 키를 못 잡음
    // 그래서 메서드 끝에서 캐시를 직접 비운다
    @Qualifier("redisCacheManager")
    private final CacheManager redisCacheManager;

    private void evictProductDetail(Long productId) {
        Cache cache = redisCacheManager.getCache("productDetail");
        if (cache != null) {
            cache.evict(productId);
        }
    }

    // 옵션 수정 (price / stock / status). stock 변경 시 ADJUSTMENT 이력 기록
    @Transactional
    public ItemUpdateResponse updateItem(Long userId, Long itemId, ItemUpdateRequest request) {
        boolean stockChanged = request.stock() != null;

        // 가격/상태만 바꿔도 옵션 행 전체가 UPDATE되므로(부분 update 아님, @Version 없음),
        // 동시 입고·주문의 재고 변경을 덮어쓰지 않도록 항상 비관적 락으로 조회한다.
        ProductItem item = productItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        validateOwner(item, userId);
        validateSellerApproved(item);
        validateParentStatus(item);

        Long beforeStock = null;
        if (stockChanged) {
            if (request.stock() > MAX_STOCK) {
                throw new CustomException(ErrorCode.INVENTORY_005);
            }
            beforeStock = item.getStock();
        }

        item.updateForAdjustment(request.price(), request.status(), request.stock());

        // 재고가 변경된 경우에만 보정 이력 기록
        if (stockChanged) {
            long afterStock = item.getStock();
            inventoryHistoryRepository.save(InventoryHistory.builder()
                    .productItem(item)
                    .type(InventoryHistoryType.ADJUSTMENT)
                    .quantity(Math.abs(afterStock - beforeStock))
                    .beforeStock(beforeStock)
                    .afterStock(afterStock)
                    .createdBy(userId)
                    .build());
        }

        evictProductDetail(item.getProduct().getId());
        return ItemUpdateResponse.from(item);
    }

    // 재고 입고. 항상 비관적 락으로 조회하고 INBOUND 이력 기록
    @Transactional
    public InboundResponse inbound(Long userId, Long itemId, InboundRequest request) {
        ProductItem item = productItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        validateOwner(item, userId);
        validateSellerApproved(item);
        validateParentStatus(item);

        long previousStock = item.getStock();
        if (previousStock + request.quantity() > MAX_STOCK) {
            throw new CustomException(ErrorCode.INVENTORY_003);
        }

        item.increaseStock(request.quantity());
        long currentStock = item.getStock();

        inventoryHistoryRepository.save(InventoryHistory.builder()
                .productItem(item)
                .type(InventoryHistoryType.INBOUND)
                .quantity(request.quantity())
                .beforeStock(previousStock)
                .afterStock(currentStock)
                .reason(request.reason())
                .createdBy(userId)
                .build());

        evictProductDetail(item.getProduct().getId());
        return new InboundResponse(itemId, previousStock, request.quantity(), currentStock);
    }

    // 본인 상품 옵션인지 검증
    private void validateOwner(ProductItem item, Long userId) {
        Long ownerId = item.getProduct().getSeller().getUser().getId();
        if (!ownerId.equals(userId)) {
            throw new CustomException(ErrorCode.PRODUCT_008);
        }
    }

    // 승인된 판매자만 옵션 쓰기 가능 (SellerProductService와 동일 정책)
    private void validateSellerApproved(ProductItem item) {
        if (item.getProduct().getSeller().getStatus() != SellerStatus.APPROVED) {
            throw new CustomException(ErrorCode.SELLER_003);
        }
    }

    // 부모 상품 상태 검증 (DISCONTINUED만 수정 불가, FORCE_INACTIVE는 허용)
    private void validateParentStatus(ProductItem item) {
        if (!item.getProduct().isEditable()) {
            throw new CustomException(ErrorCode.PRODUCT_010);
        }
    }
}
