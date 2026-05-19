package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@Builder
public class ProductDetailResponse {

    private Long productId;
    private String name;
    private String description;
    private String thumbnailUrl;
    private long viewCount;
    private long salesCount;
    private String shopName;
    private List<String> optionNames;
    private List<ProductItemResponse> items;
    private List<String> imageUrls;
    private List<String> categoryNames;

    public static ProductDetailResponse from(Product product) {
        List<String> optionNames = buildOptionNames(product);

        List<ProductItemResponse> items = product.getProductItems().stream()
            .filter(ProductItem::isOnSale)
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
            .viewCount(product.getViewCount())
            .salesCount(product.getSalesCount())
            .shopName(product.getSeller().getShopName())
            .optionNames(optionNames)
            .items(items)
            .imageUrls(imageUrls)
            .categoryNames(categoryNames)
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
