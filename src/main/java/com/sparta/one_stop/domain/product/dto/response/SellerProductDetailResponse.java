package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 판매자 본인 상품 단건 상세 응답.
// 구매자용(BuyerProductDetailResponse)은 APPROVED 상품만·ON_SALE 옵션만·재고 숨김이라
// 판매자가 미승인(APPROVE_REQUESTED) 상품이나 판매중단(STOP) 옵션을 관리할 수 없다.
// 따라서 상품 상태(status)와 전체 옵션(STOP 포함, 재고·옵션 상태 포함)을 노출한다.
@Getter
@Builder
public class SellerProductDetailResponse {

    private Long productId;
    private String name;
    private String description;
    private String thumbnailUrl;
    private ProductStatus status;
    private long viewCount;
    private long salesCount;
    private String shopName;
    private List<String> optionNames;
    private List<SellerProductItemResponse> items;
    private List<String> imageUrls;
    private List<String> categoryNames;
    private List<String> tags;

    public static SellerProductDetailResponse from(Product product) {
        List<String> optionNames = buildOptionNames(product);

        // 판매자 관리 화면이므로 STOP 옵션도 포함해 전체 옵션을 노출한다.
        List<SellerProductItemResponse> items = product.getProductItems().stream()
            .map(SellerProductItemResponse::from)
            .toList();

        List<String> imageUrls = product.getProductImages().stream()
            .filter(ProductImage::isActive)
            .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
            .map(ProductImage::getImageUrl)
            .toList();

        List<String> categoryNames = product.getCategoryMappings().stream()
            .map(m -> m.getCategory().getName())
            .toList();

        return SellerProductDetailResponse.builder()
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
