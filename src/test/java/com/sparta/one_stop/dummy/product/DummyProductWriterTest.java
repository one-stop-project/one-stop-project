package com.sparta.one_stop.dummy.product;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.dummy.grouping.GroupedProduct;
import com.sparta.one_stop.dummy.grouping.ProductVariant;
import com.sparta.one_stop.dummy.source.DummyProductSourceGroup;
import com.sparta.one_stop.dummy.source.DummyProductSourceGroupRepository;
import com.sparta.one_stop.dummy.source.DummyProductSourceListing;
import com.sparta.one_stop.dummy.source.DummyProductSourceListingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DummyProductWriter")
class DummyProductWriterTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final DummyProductSourceGroupRepository sourceGroupRepository = mock(DummyProductSourceGroupRepository.class);
    private final DummyProductSourceListingRepository sourceListingRepository = mock(DummyProductSourceListingRepository.class);

    private final DummyProductWriter writer = new DummyProductWriter(
        productRepository, categoryRepository, sourceGroupRepository, sourceListingRepository);

    private final GroupedProduct grouped = new GroupedProduct(
        "NAVER|base1", "갤럭시 S24", "설명", List.of(1L), List.of("용량"), "img",
        List.of(new ProductVariant("NAVER|pid:g256", List.of("256GB"), 1100000L)));

    @Test
    @DisplayName("신규 그룹 → Product 생성 + 그룹·변형 매핑 저장, CREATED 반환")
    void createsWhenGroupAbsent() {
        Seller seller = mock(Seller.class);
        when(sourceGroupRepository.findBySourceAndBaseSourceKey(any(), any())).thenReturn(Optional.empty());
        when(categoryRepository.findAllByIdIn(any())).thenReturn(List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        DummyWriteResult result = writer.write(seller, grouped);

        assertThat(result).isEqualTo(DummyWriteResult.CREATED);
        verify(sourceGroupRepository).save(any());
        verify(sourceListingRepository).save(any());
    }

    @Test
    @DisplayName("기존 그룹 → 그룹 재생성 안 함(가격 갱신/변형 추가 경로), UPDATED 반환")
    void updatesWhenGroupExists() {
        Seller seller = mock(Seller.class);
        DummyProductSourceGroup existing = mock(DummyProductSourceGroup.class);
        when(existing.getProductId()).thenReturn(10L);
        when(sourceGroupRepository.findBySourceAndBaseSourceKey(any(), any())).thenReturn(Optional.of(existing));

        Product product = mock(Product.class);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(product.getProductItems()).thenReturn(new HashSet<>());
        when(sourceListingRepository.findAllBySourceAndBaseSourceKey(any(), any())).thenReturn(List.of());
        when(productRepository.saveAndFlush(any(Product.class))).thenReturn(product);

        DummyWriteResult result = writer.write(seller, grouped);

        assertThat(result).isEqualTo(DummyWriteResult.UPDATED);
        verify(sourceGroupRepository, never()).save(any());
    }

    @Test
    @DisplayName("그룹키는 없지만 변형이 이미 저장돼 있으면 기존 그룹으로 라우팅 (UPDATED, 신규 생성 안 함)")
    void routesToExistingGroupWhenListingExists() {
        Seller seller = mock(Seller.class);
        // 다변형↔단일 전환으로 그룹키가 바뀐 채 들어온 경우 — 그룹키로는 못 찾음
        GroupedProduct flipped = new GroupedProduct(
            "NAVER|pid:g256", "갤럭시 S24 256", "설명", List.of(1L), List.of(), "img",
            List.of(new ProductVariant("NAVER|pid:g256", List.of(), 1100000L)));
        when(sourceGroupRepository.findBySourceAndBaseSourceKey("NAVER", "NAVER|pid:g256"))
            .thenReturn(Optional.empty());
        // 같은 변형이 이미 다른 그룹(NAVER|base1)에 저장돼 있음
        DummyProductSourceListing existingListing = mock(DummyProductSourceListing.class);
        when(existingListing.getBaseSourceKey()).thenReturn("NAVER|base1");
        when(sourceListingRepository.findBySourceAndListingSourceKey("NAVER", "NAVER|pid:g256"))
            .thenReturn(Optional.of(existingListing));
        DummyProductSourceGroup existingGroup = mock(DummyProductSourceGroup.class);
        when(existingGroup.getProductId()).thenReturn(99L);
        when(existingGroup.getBaseSourceKey()).thenReturn("NAVER|base1");
        when(sourceGroupRepository.findBySourceAndBaseSourceKey("NAVER", "NAVER|base1"))
            .thenReturn(Optional.of(existingGroup));
        // 라우팅된 그룹의 상품은 정상 존재 → 가격 갱신 경로(UPDATED)
        Product product = mock(Product.class);
        when(productRepository.findById(99L)).thenReturn(Optional.of(product));
        when(product.getProductItems()).thenReturn(new HashSet<>());
        when(sourceListingRepository.findAllBySourceAndBaseSourceKey("NAVER", "NAVER|base1")).thenReturn(List.of());

        DummyWriteResult result = writer.write(seller, flipped);

        assertThat(result).isEqualTo(DummyWriteResult.UPDATED);
        verify(sourceGroupRepository, never()).save(any());  // 신규 그룹 생성 안 함
        verify(productRepository, never()).save(any());       // 신규 Product 생성 안 함
    }

    @Test
    @DisplayName("매핑은 있으나 상품이 삭제된 stale 그룹 → SKIPPED (UPDATED 오보고 안 함, 재생성 안 함)")
    void skipsWhenProductStale() {
        Seller seller = mock(Seller.class);
        DummyProductSourceGroup existing = mock(DummyProductSourceGroup.class);
        when(existing.getProductId()).thenReturn(10L);
        when(existing.getBaseSourceKey()).thenReturn("NAVER|base1");
        when(sourceGroupRepository.findBySourceAndBaseSourceKey(any(), any())).thenReturn(Optional.of(existing));
        when(productRepository.findById(10L)).thenReturn(Optional.empty());  // 상품이 삭제됨

        DummyWriteResult result = writer.write(seller, grouped);

        assertThat(result).isEqualTo(DummyWriteResult.SKIPPED);
        verify(sourceGroupRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }
}
