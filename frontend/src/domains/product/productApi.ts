import { apiClient, ApiResponse, PageResponse } from '@/api/client';

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  공통 타입 — 백엔드 ProductSummaryResponse 와 1:1
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

export interface ProductSummary {
  productId: number;
  name: string;
  thumbnailUrl: string | null;
  minPrice: number;      // 백엔드: minPrice (price 아님!)
  salesCount: number;
  viewCount: number;
}

// 백엔드 ProductItemResponse 와 1:1
export interface ProductItem {
  itemId: number;
  optionName: string;    // 백엔드: optionName (name 아님!)
  price: number;
  stock: number;
}

// 백엔드 ProductDetailResponse 와 1:1
export interface ProductDetail {
  productId: number;
  name: string;
  description: string;
  thumbnailUrl: string | null;
  viewCount: number;
  salesCount: number;
  shopName: string;            // 백엔드: shopName (sellerName 아님!)
  optionNames: string[];
  items: ProductItem[];
  imageUrls: string[];         // 백엔드: imageUrls (images 아님!)
  categoryNames: string[];     // 백엔드: categoryNames
}

// 백엔드 CategoryTreeResponse 와 1:1
export interface Category {
  categoryId: number;
  name: string;
  children: Category[];
}

export interface ProductSearchParams {
  page?: number;
  size?: number;
  keyword?: string;
  categoryId?: number;
  // ★ 백엔드는 Spring Pageable 정렬 사용: "필드,방향" (예: "id,desc")
  sort?: string;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  API
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

export const productApi = {
  // 상품 목록/검색 — Page 응답
  getList: async (params: ProductSearchParams = {}): Promise<PageResponse<ProductSummary>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<ProductSummary>>>('/products', {
      params,
    });
    return res.data.data;
  },

  // 인기 상품 — ★ Page 응답 (배열 아님!)
  getPopular: async (size: number = 8): Promise<PageResponse<ProductSummary>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<ProductSummary>>>('/products/popular', {
      params: { size },
    });
    return res.data.data;
  },

  // 상품 상세
  getDetail: async (productId: number): Promise<ProductDetail> => {
    const res = await apiClient.get<ApiResponse<ProductDetail>>(`/products/${productId}`);
    return res.data.data;
  },

  // 관련 상품 — List 응답 (배열 맞음)
  getRelated: async (productId: number): Promise<ProductSummary[]> => {
    const res = await apiClient.get<ApiResponse<ProductSummary[]>>(
      `/products/${productId}/related`
    );
    return res.data.data;
  },

  // 카테고리 트리
  getCategories: async (): Promise<Category[]> => {
    const res = await apiClient.get<ApiResponse<Category[]>>('/categories');
    return res.data.data;
  },
};
