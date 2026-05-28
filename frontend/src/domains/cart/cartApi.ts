import { apiClient, ApiResponse } from '@/api/client';

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  타입
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

export interface CartItem {
  cartItemId: number;
  productId: number;
  productItemId: number;
  productName: string;
  itemName: string; // 옵션명
  thumbnailUrl: string | null;
  price: number;
  quantity: number;
  stock: number; // 재고 (품절 표시용)
  subtotal: number;
}

export interface CartResponse {
  cartId: number;
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
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

  addItem: async (data: AddCartItemRequest): Promise<CartItem> => {
    const res = await apiClient.post<ApiResponse<CartItem>>('/carts/items', data);
    return res.data.data;
  },

  updateQuantity: async (itemId: number, data: UpdateCartItemRequest): Promise<CartItem> => {
    const res = await apiClient.patch<ApiResponse<CartItem>>(`/carts/items/${itemId}`, data);
    return res.data.data;
  },

  removeItem: async (itemId: number): Promise<void> => {
    await apiClient.delete(`/carts/items/${itemId}`);
  },
};
