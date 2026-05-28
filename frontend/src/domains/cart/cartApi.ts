import { apiClient, ApiResponse } from '@/api/client';

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  타입 — 백엔드 실제 DTO와 1:1 매칭
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// 백엔드 CartItemDetailResponse 와 1:1
export interface CartItem {
  cartItemId: number | null;   // 비로그인 시 null
  itemId: number;              // ★ 수정/삭제 기준값 (productItemId 아님)
  productId: number;
  productName: string;
  optionName: string;          // ★ itemName 아님
  price: number;
  quantity: number;
  thumbnailUrl: string | null;
  stock: number;
  available: boolean;          // 재고>0 && 판매중
}

// 백엔드 CartPageResponse 와 1:1
export interface CartResponse {
  cartId: number;
  content: CartItem[];         // ★ items 아님!
  totalPrice: number;
  page: number;
  size: number;
  totalPages: number;
}

// 백엔드 CartItemResponse (담기 응답)
export interface AddCartItemResponse {
  cartItemId: number | null;
  itemId: number;
  quantity: number;
  message: string;
}

// 백엔드 UpdateCartItemResponse (수정 응답)
export interface UpdateCartItemResponse {
  itemId: number;
  quantity: number;
}

export interface AddCartItemRequest {
  productItemId: number;
  quantity: number;
}

export interface UpdateCartItemRequest {
  quantity: number;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  API
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

export const cartApi = {
  getCart: async (): Promise<CartResponse> => {
    const res = await apiClient.get<ApiResponse<CartResponse>>('/carts');
    return res.data.data;
  },

  addItem: async (data: AddCartItemRequest): Promise<AddCartItemResponse> => {
    const res = await apiClient.post<ApiResponse<AddCartItemResponse>>('/carts/items', data);
    return res.data.data;
  },

  // ★ 수정/삭제 기준은 itemId (옵션 ID)
  updateQuantity: async (
    itemId: number,
    data: UpdateCartItemRequest
  ): Promise<UpdateCartItemResponse> => {
    const res = await apiClient.patch<ApiResponse<UpdateCartItemResponse>>(
      `/carts/items/${itemId}`,
      data
    );
    return res.data.data;
  },

  removeItem: async (itemId: number): Promise<void> => {
    await apiClient.delete(`/carts/items/${itemId}`);
  },
};
