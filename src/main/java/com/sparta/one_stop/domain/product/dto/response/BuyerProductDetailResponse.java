package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 구매자용 상품 상세 응답 — 옵션 재고 수량(stock)을 노출하지 않는다(BuyerProductItemResponse = 품절 여부만).
// 판매자/관리자용 상세는 ProductDetailResponse(stock 포함)를 그대로 사용한다.
@Getter
@Builder
public class BuyerProductDetailResponse {

    private Long productId;
    private String name;
    private String description;
    private String thumbnailUrl;
    private long viewCount;
    private long salesCount;
    private String shopName;
    private List<String> optionNames;
    private List<BuyerProductItemResponse> items;
    private List<String> imageUrls;
    private List<String> categoryNames;
    private List<String> tags;

    public static BuyerProductDetailResponse from(Product product) {
        List<String> optionNames = buildOptionNames(product);

        List<BuyerProductItemResponse> items = product.getProductItems().stream()
            .filter(ProductItem::isOnSale)
            .map(BuyerProductItemResponse::from)
            .toList();

        List<String> imageUrls = product.getProductImages().stream()
            .filter(ProductImage::isActive)
            .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
            .map(ProductImage::getImageUrl)
            .toList();

        List<String> categoryNames = product.getCategoryMappings().stream()
            .map(m -> m.getCategory().getName())
            .toList();

        return BuyerProductDetailResponse.builder()
            .productId(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .thumbnailUrl(product.getThumbnailUrl())
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
