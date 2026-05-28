import { apiClient, ApiResponse, PageResponse } from '@/api/client';
import { ProductStatus } from '@/types/common';
import { ProductItem } from '@/domains/product/productApi';

// 백엔드 이미지 관련 응답 (실제 DTO에 맞춤)
export interface ProductImageAddResponse {
  addedImageCount: number;
  totalImageCount: number;
  thumbnailUrl: string;
}

export interface ProductImageDeleteResponse {
  deletedImageId: number;
  remainingImageCount: number;
  thumbnailUrl: string;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  판매자 상품
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

export interface SellerProduct {
  productId: number;
  name: string;
  description: string;
  price: number;
  categoryName: string;
  status: ProductStatus;
  thumbnailUrl: string | null;
  totalStock: number;
  createdAt: string;
}

export interface ProductCreateRequest {
  name: string;
  description: string;
  price: number;
  categoryId: number;
  items: Array<{
    name: string;
    price: number;
    stock: number;
  }>;
}

export interface ProductUpdateRequest {
  name?: string;
  description?: string;
  price?: number;
  categoryId?: number;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  판매자 주문
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

export interface SellerOrderItem {
  orderItemId: number;
  orderId: number;
  productName: string;
  itemName: string;
  quantity: number;
  price: number;
  receiverName: string;
  shippingAddress: string;
  status: string;
  createdAt: string;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  API
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

export const sellerApi = {
  // 상품
  getMyProducts: async (page = 0, size = 20): Promise<PageResponse<SellerProduct>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<SellerProduct>>>('/seller/products', {
      params: { page, size },
    });
    return res.data.data;
  },

  createProduct: async (data: ProductCreateRequest): Promise<SellerProduct> => {
    const res = await apiClient.post<ApiResponse<SellerProduct>>('/seller/products', data);
    return res.data.data;
  },

  updateProduct: async (productId: number, data: ProductUpdateRequest): Promise<SellerProduct> => {
    const res = await apiClient.patch<ApiResponse<SellerProduct>>(
      `/seller/products/${productId}`,
      data
    );
    return res.data.data;
  },

  deleteProduct: async (productId: number): Promise<void> => {
    await apiClient.delete(`/seller/products/${productId}`);
  },

  // 재고 입고
  inbound: async (itemId: number, quantity: number): Promise<void> => {
    await apiClient.post(`/seller/items/${itemId}/inbound`, { quantity });
  },

  updateItem: async (
    itemId: number,
    data: { name?: string; price?: number }
  ): Promise<ProductItem> => {
    const res = await apiClient.patch<ApiResponse<ProductItem>>(`/seller/items/${itemId}`, data);
    return res.data.data;
  },

  // 주문
  getMyOrders: async (page = 0, size = 20): Promise<PageResponse<SellerOrderItem>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<SellerOrderItem>>>('/seller/orders', {
      params: { page, size },
    });
    return res.data.data;
  },

  confirmOrder: async (orderItemId: number): Promise<void> => {
    await apiClient.post(`/seller/orders/${orderItemId}/confirm`);
  },

  rejectOrder: async (orderItemId: number, reason: string): Promise<void> => {
    await apiClient.post(`/seller/orders/${orderItemId}/reject`, { reason });
  },

  shipDelivery: async (
    deliveryId: number,
    data: { carrierName: string; trackingNumber: string }
  ): Promise<void> => {
    await apiClient.post(`/seller/deliveries/${deliveryId}/ship`, data);
  },

  // 배송 상태 변경 (누락 보강)
  updateDeliveryStatus: async (deliveryId: number, status: string): Promise<void> => {
    await apiClient.patch(`/seller/deliveries/${deliveryId}/status`, { status });
  },

  // 주문 거절 (누락 보강)
  rejectOrderItem: async (orderItemId: number, reason: string): Promise<void> => {
    await apiClient.post(`/seller/orders/${orderItemId}/reject`, { reason });
  },

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  //  상품 이미지 관리 (누락 보강)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  addProductImage: async (
    productId: number,
    imageUrls: string[]
  ): Promise<ProductImageAddResponse> => {
    const res = await apiClient.post<ApiResponse<ProductImageAddResponse>>(
      `/seller/products/${productId}/images`,
      { imageUrls }
    );
    return res.data.data;
  },

  deleteProductImage: async (
    productId: number,
    imageId: number
  ): Promise<ProductImageDeleteResponse> => {
    const res = await apiClient.delete<ApiResponse<ProductImageDeleteResponse>>(
      `/seller/products/${productId}/images/${imageId}`
    );
    return res.data.data;
  },

  setThumbnail: async (productId: number, imageId: number): Promise<void> => {
    await apiClient.patch(`/seller/products/${productId}/images/${imageId}/thumbnail`);
  },
};
