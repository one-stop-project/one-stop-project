package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 판매자/관리자용 상품 상세 응답 — 상품 상태(status), 전체 옵션(판매중단 STOP 포함), 옵션별 재고를 노출한다.
// 판매자 단건 상세 조회(GET)와 상품 수정(PATCH) 응답에 공통 사용한다.
// (구매자용 BuyerProductDetailResponse는 APPROVED 상품만·ON_SALE 옵션만·재고 숨김)
@Getter
@Builder
public class ProductDetailResponse {

    private Long productId;
    private String name;
    private String description;
    private String thumbnailUrl;
    private ProductStatus status;
    private long viewCount;
    private long salesCount;
    private String shopName;
    private List<String> optionNames;
    private List<ProductItemResponse> items;
    private List<String> imageUrls;
    private List<String> categoryNames;
    private List<String> tags;
    // 관리자 반려 사유 — REJECTED 상품에서만 채워지며, 그 외에는 null이다.
    private String rejectReason;

    public static ProductDetailResponse from(Product product) {
        return from(product, null);
    }

    public static ProductDetailResponse from(Product product, String rejectReason) {
        List<String> optionNames = buildOptionNames(product);

        // 판매자 관리 화면이므로 STOP 옵션도 포함해 전체 옵션을 노출한다.
        List<ProductItemResponse> items = product.getProductItems().stream()
            .map(ProductItemResponse::from)
            .toList();

        List<String> imageUrls = product.getProductImages().stream()
            .filter(ProductImage::isActive)
            .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
            .map(ProductImage::getImageUrl)
            .toList();

        List<String> categoryNames = product.getCategoryMappings().stream()
            .map(m -> m.getCategory().getName())
            .toList();

        return ProductDetailResponse.builder()
            .productId(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .thumbnailUrl(product.getThumbnailUrl())
            .status(product.getStatus())
            .viewCount(product.getViewCount())
            .salesCount(product.getSalesCount())
            .shopName(product.getSeller().getShopName())
            .optionNames(optionNames)
            .items(items)
            .imageUrls(imageUrls)
            .categoryNames(categoryNames)
            .tags(product.getTags().stream().sorted().toList())
            .rejectReason(rejectReason)
            .build();
    }

    private static List<String> buildOptionNames(Product product) {
        List<String> names = new ArrayList<>();
        addIfNotBlank(names, product.getOptionName1());
        addIfNotBlank(names, product.getOptionName2());
        addIfNotBlank(names, product.getOptionName3());
        addIfNotBlank(names, product.getOptionName4());
        addIfNotBlank(names, product.getOptionName5());
        return names;
    }

    private static void addIfNotBlank(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value);
        }
    }
}
