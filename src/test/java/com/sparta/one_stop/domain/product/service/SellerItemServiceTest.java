package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.one_stop.domain.product.dto.request.InboundRequest;
import com.sparta.one_stop.domain.product.dto.request.ItemUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.InboundResponse;
import com.sparta.one_stop.domain.product.dto.response.ItemUpdateResponse;
import com.sparta.one_stop.domain.product.entity.InventoryHistory;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.InventoryHistoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.product.InventoryHistoryType;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerItemServiceTest {

    @Mock
    private ProductItemRepository productItemRepository;

    @Mock
    private InventoryHistoryRepository inventoryHistoryRepository;

    @Mock
    private org.springframework.cache.CacheManager redisCacheManager;

    @InjectMocks
    private SellerItemService sellerItemService;

    private static final Long SELLER_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long ITEM_ID = 10L;

    // ===== 객체 생성 헬퍼 =====

    // 실제 엔티티 그래프(User-Seller-Product-ProductItem)를 만들어 반환
    private ProductItem createItem(Long ownerUserId, ProductStatus productStatus,
                                   long price, long stock) {
        return createItem(ownerUserId, productStatus, SellerStatus.APPROVED, price, stock);
    }

    // sellerStatus를 지정해 판매자 상태별(승인/반려 등) 옵션을 만든다
    private ProductItem createItem(Long ownerUserId, ProductStatus productStatus,
                                   SellerStatus sellerStatus, long price, long stock) {
        User user = User.builder()
                .email("seller@test.com")
                .password("password")
                .name("판매자")
                .role(UserRole.SELLER)
                .build();
        ReflectionTestUtils.setField(user, "id", ownerUserId);

        Seller seller = Seller.builder()
                .user(user)
                .shopName("테스트샵")
                .businessNumber("1234567890")
                .build();
        ReflectionTestUtils.setField(seller, "id", 100L);
        applySellerStatus(seller, sellerStatus);

        Product product = Product.builder()
                .seller(seller)
                .name("테스트상품")
                .build();
        applyProductStatus(product, productStatus);

        ProductItem item = ProductItem.builder()
                .product(product)
                .optionValue1("기본")
                .optionValue2("")
                .optionValue3("")
                .optionValue4("")
                .optionValue5("")
                .price(price)
                .stock(stock)
                .build();
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    private void applySellerStatus(Seller seller, SellerStatus status) {
        switch (status) {
            case APPROVED -> seller.approve();
            case REJECTED -> seller.reject();
            default -> {
                // PENDING/SUSPENDED: 빌더 기본값(PENDING) 유지
            }
        }
    }

    private void applyProductStatus(Product product, ProductStatus status) {
        switch (status) {
            case APPROVED -> product.approve();
            case REJECTED -> product.reject();
            case DISCONTINUED -> product.discontinue();
            case FORCE_INACTIVE -> product.forceInactive();
            case APPROVE_REQUESTED -> {
                // 생성 시 기본값이므로 별도 처리 없음
            }
        }
    }

    // ===== 목 세팅 헬퍼 =====

    // updateItem은 이제 항상 비관적 락(findByIdForUpdate)으로 조회한다 (#441)
    private void mockFindItem(ProductItem item) {
        given(productItemRepository.findByIdForUpdate(ITEM_ID)).willReturn(Optional.of(item));
    }

    private void mockFindItemForUpdate(ProductItem item) {
        given(productItemRepository.findByIdForUpdate(ITEM_ID)).willReturn(Optional.of(item));
    }

    @Nested
    @DisplayName("updateItem - 옵션 수정")
    class UpdateItem {

        @Test
        @DisplayName("price만 수정해도 비관적 락으로 조회한다 — 동시 재고 변경 덮어쓰기 방지 (#441)")
        void updateItem_priceOnly_usesPessimisticLock() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 50L);
            mockFindItem(item);
            ItemUpdateRequest request = new ItemUpdateRequest(2000L, null, null);

            // when
            ItemUpdateResponse response =
                    sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request);

            // then
            assertThat(response.price()).isEqualTo(2000L);
            verify(productItemRepository).findByIdForUpdate(ITEM_ID);
            verify(productItemRepository, never()).findById(anyLong());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("stock 포함 수정 시 비관적 락으로 조회하고 ADJUSTMENT 이력을 남긴다")
        void updateItem_withStock_locksAndRecordsAdjustment() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 50L);
            mockFindItemForUpdate(item);
            ItemUpdateRequest request = new ItemUpdateRequest(null, 200L, null);

            // when
            ItemUpdateResponse response =
                    sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request);

            // then
            assertThat(response.stock()).isEqualTo(200L);
            verify(productItemRepository).findByIdForUpdate(ITEM_ID);
            verify(productItemRepository, never()).findById(anyLong());

            ArgumentCaptor<InventoryHistory> captor =
                    ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            InventoryHistory history = captor.getValue();
            assertThat(history.getType()).isEqualTo(InventoryHistoryType.ADJUSTMENT);
            assertThat(history.getBeforeStock()).isEqualTo(50L);
            assertThat(history.getAfterStock()).isEqualTo(200L);
            assertThat(history.getQuantity()).isEqualTo(150L);
            assertThat(history.getCreatedBy()).isEqualTo(SELLER_USER_ID);
        }

        @Test
        @DisplayName("status만 수정하면 반영되고 이력은 남기지 않는다")
        void updateItem_statusOnly_succeedsWithoutHistory() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 50L);
            mockFindItem(item);
            ItemUpdateRequest request = new ItemUpdateRequest(null, null, ProductItemStatus.STOP);

            // when
            ItemUpdateResponse response =
                    sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request);

            // then
            assertThat(response.status()).isEqualTo(ProductItemStatus.STOP);
            verify(productItemRepository).findByIdForUpdate(ITEM_ID);
            verify(productItemRepository, never()).findById(anyLong());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("모든 필드가 null이면 변경 없이 성공 응답을 반환한다")
        void updateItem_allFieldsNull_succeedsWithNoChange() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 50L);
            mockFindItem(item);
            ItemUpdateRequest request = new ItemUpdateRequest(null, null, null);

            // when
            ItemUpdateResponse response =
                    sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request);

            // then
            assertThat(response.price()).isEqualTo(1000L);
            assertThat(response.stock()).isEqualTo(50L);
            assertThat(response.status()).isEqualTo(ProductItemStatus.ON_SALE);
            verify(productItemRepository).findByIdForUpdate(ITEM_ID);
            verify(productItemRepository, never()).findById(anyLong());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("itemId에 해당하는 옵션이 없으면 PRODUCT_001 예외가 발생한다")
        void updateItem_itemNotFound_throwsProduct001() {
            // given
            given(productItemRepository.findByIdForUpdate(ITEM_ID)).willReturn(Optional.empty());
            ItemUpdateRequest request = new ItemUpdateRequest(2000L, null, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_001);
        }

        @Test
        @DisplayName("다른 판매자의 상품 옵션이면 PRODUCT_008 예외가 발생한다")
        void updateItem_otherSellerProduct_throwsProduct008() {
            // given
            ProductItem item = createItem(OTHER_USER_ID, ProductStatus.APPROVED, 1000L, 50L);
            mockFindItem(item);
            ItemUpdateRequest request = new ItemUpdateRequest(2000L, null, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_008);
        }

        @Test
        @DisplayName("부모 상품이 DISCONTINUED 상태면 PRODUCT_010 예외가 발생한다")
        void updateItem_parentDiscontinued_throwsProduct010() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.DISCONTINUED, 1000L, 50L);
            mockFindItem(item);
            ItemUpdateRequest request = new ItemUpdateRequest(2000L, null, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_010);
        }

        @Test
        @DisplayName("부모 상품이 FORCE_INACTIVE 상태면 소명/재승인을 위해 수정이 허용된다")
        void updateItem_parentForceInactive_succeeds() {
            // given
            ProductItem item =
                    createItem(SELLER_USER_ID, ProductStatus.FORCE_INACTIVE, 1000L, 50L);
            mockFindItem(item);
            ItemUpdateRequest request = new ItemUpdateRequest(2000L, null, null);

            // when
            ItemUpdateResponse response =
                    sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request);

            // then
            assertThat(response.price()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("stock 절대값이 한도(99,999)를 초과하면 보정 전용 INVENTORY_005 예외가 발생한다")
        void updateItem_stockExceedsMax_throwsInventory005() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 50L);
            mockFindItemForUpdate(item);
            ItemUpdateRequest request = new ItemUpdateRequest(null, 100_000L, null);

            // when & then — 보정 경로이므로 입고 문구(INVENTORY_003)가 아닌 보정 전용 메시지를 던진다
            assertThatThrownBy(
                    () -> sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVENTORY_005);
        }

        @Test
        @DisplayName("판매자가 승인 상태가 아니면(반려) SELLER_003 예외가 발생하고 이력을 남기지 않는다")
        void updateItem_sellerNotApproved_throwsSeller003() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED,
                    SellerStatus.REJECTED, 1000L, 50L);
            mockFindItem(item);
            ItemUpdateRequest request = new ItemUpdateRequest(2000L, null, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.updateItem(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SELLER_003);
            verify(inventoryHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("inbound - 재고 입고")
    class Inbound {

        @Test
        @DisplayName("정상 입고 시 비관적 락으로 조회하고 INBOUND 이력을 남긴다")
        void inbound_validQuantity_increasesStockAndRecordsHistory() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 80L);
            mockFindItemForUpdate(item);
            InboundRequest request = new InboundRequest(50L, null);

            // when
            InboundResponse response =
                    sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request);

            // then
            assertThat(response.itemId()).isEqualTo(ITEM_ID);
            assertThat(response.previousStock()).isEqualTo(80L);
            assertThat(response.addedQuantity()).isEqualTo(50L);
            assertThat(response.currentStock()).isEqualTo(130L);

            ArgumentCaptor<InventoryHistory> captor =
                    ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            InventoryHistory history = captor.getValue();
            assertThat(history.getType()).isEqualTo(InventoryHistoryType.INBOUND);
            assertThat(history.getBeforeStock()).isEqualTo(80L);
            assertThat(history.getAfterStock()).isEqualTo(130L);
            assertThat(history.getQuantity()).isEqualTo(50L);
            assertThat(history.getCreatedBy()).isEqualTo(SELLER_USER_ID);
        }

        @Test
        @DisplayName("reason이 포함되면 이력에 사유가 저장된다")
        void inbound_withReason_savesReason() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 80L);
            mockFindItemForUpdate(item);
            InboundRequest request = new InboundRequest(50L, "정기 입고");

            // when
            sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request);

            // then
            ArgumentCaptor<InventoryHistory> captor =
                    ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getReason()).isEqualTo("정기 입고");
        }

        @Test
        @DisplayName("reason이 null이면 이력의 사유도 null로 저장된다")
        void inbound_nullReason_savesHistoryWithNullReason() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 80L);
            mockFindItemForUpdate(item);
            InboundRequest request = new InboundRequest(50L, null);

            // when
            sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request);

            // then
            ArgumentCaptor<InventoryHistory> captor =
                    ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getReason()).isNull();
        }

        @Test
        @DisplayName("itemId에 해당하는 옵션이 없으면 PRODUCT_001 예외가 발생한다")
        void inbound_itemNotFound_throwsProduct001() {
            // given
            given(productItemRepository.findByIdForUpdate(ITEM_ID)).willReturn(Optional.empty());
            InboundRequest request = new InboundRequest(50L, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_001);
        }

        @Test
        @DisplayName("다른 판매자의 상품 옵션이면 PRODUCT_008 예외가 발생한다")
        void inbound_otherSellerProduct_throwsProduct008() {
            // given
            ProductItem item = createItem(OTHER_USER_ID, ProductStatus.APPROVED, 1000L, 80L);
            mockFindItemForUpdate(item);
            InboundRequest request = new InboundRequest(50L, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_008);
        }

        @Test
        @DisplayName("부모 상품이 DISCONTINUED 상태면 PRODUCT_010 예외가 발생한다")
        void inbound_parentDiscontinued_throwsProduct010() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.DISCONTINUED, 1000L, 80L);
            mockFindItemForUpdate(item);
            InboundRequest request = new InboundRequest(50L, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_010);
        }

        @Test
        @DisplayName("입고 후 재고가 한도(99,999)를 초과하면 INVENTORY_003 예외가 발생한다")
        void inbound_exceedsMaxStock_throwsInventory003() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 50_000L);
            mockFindItemForUpdate(item);
            InboundRequest request = new InboundRequest(50_000L, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVENTORY_003);
        }

        @Test
        @DisplayName("재고가 99,999인 상태에서 1개 입고를 시도하면 INVENTORY_003 예외가 발생한다")
        void inbound_atMaxStockPlusOne_throwsInventory003() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED, 1000L, 99_999L);
            mockFindItemForUpdate(item);
            InboundRequest request = new InboundRequest(1L, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVENTORY_003);
        }

        @Test
        @DisplayName("판매자가 승인 상태가 아니면(반려) SELLER_003 예외가 발생하고 이력을 남기지 않는다")
        void inbound_sellerNotApproved_throwsSeller003() {
            // given
            ProductItem item = createItem(SELLER_USER_ID, ProductStatus.APPROVED,
                    SellerStatus.REJECTED, 1000L, 80L);
            mockFindItemForUpdate(item);
            InboundRequest request = new InboundRequest(50L, null);

            // when & then
            assertThatThrownBy(
                    () -> sellerItemService.inbound(SELLER_USER_ID, ITEM_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SELLER_003);
            verify(inventoryHistoryRepository, never()).save(any());
        }
    }
}
