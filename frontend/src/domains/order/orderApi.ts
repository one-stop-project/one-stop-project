import {apiClient, ApiResponse, PageResponse} from '@/api/client';
import {OrderStatus, PaymentMethod} from '@/types/common';

// ════════════════════════════════════════════════════════════
//  P1-④ 수정: 백엔드 CreateOrderRequest / CreateOrderItemRequest와 필드명 일치
//
//  변경 내역:
//   - items[].productItemId  →  items[].itemId
//   - shippingAddress        →  receiverAddress
//   - message                →  deliveryMessage
//   + orderType, cartItemIds, userCouponId, usedPoint 추가 (백엔드 지원 필드)
// ════════════════════════════════════════════════════════════

export type OrderType = 'DIRECT' | 'CART';

export interface OrderItem {
  orderItemId: number;
  productId: number;
  productName: string;
  itemName: string;
  thumbnailUrl: string | null;
  price: number;
  quantity: number;
  subtotal: number;
  sellerName: string;
  status: OrderStatus;
}

export interface Order {
  orderId: number;
  orderNumber: string;
  status: OrderStatus;
  items: OrderItem[];
  totalPrice: number;
  shippingFee: number;
  finalPrice: number;
  receiverName: string;
  receiverPhone: string;
  receiverAddress: string;       // ★ shippingAddress → receiverAddress
  deliveryMessage?: string;      // ★ message → deliveryMessage
  paymentMethod?: PaymentMethod;
  createdAt: string;
  shippingAddress: string;
}

export interface CreateOrderItemRequest {
  itemId: number;                // ★ productItemId → itemId (백엔드 @NotNull)
  quantity: number;              // 1 이상 (백엔드 @Min(1))
}

export interface CreateOrderRequest {
  orderType?: OrderType;         // DIRECT(즉시구매) | CART(장바구니)
  items?: CreateOrderItemRequest[];   // 직접 구매 시
  cartItemIds?: number[];        // 장바구니 주문 시 선택된 cartItem ID 목록
  receiverName: string;          // @NotBlank
  receiverPhone: string;         // @NotBlank
  receiverAddress: string;       // ★ @NotBlank (shippingAddress 아님)
  deliveryMessage?: string;      // ★ 최대 50자 (message 아님)
  userCouponId?: number;         // 적용할 쿠폰 (선택)
  usedPoint?: number;            // 사용 포인트 (0 이상, 선택)
}

export interface OrderListParams {
  page?: number;
  size?: number;
  status?: OrderStatus;
}

export const orderApi = {
  create: async (data: CreateOrderRequest): Promise<Order> => {
    const res = await apiClient.post<ApiResponse<Order>>('/orders', data);
    return res.data.data;
  },

  getMyOrders: async (params: OrderListParams = {}): Promise<PageResponse<Order>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<Order>>>('/orders', { params });
    return res.data.data;
  },

  getDetail: async (orderId: number): Promise<Order> => {
    const res = await apiClient.get<ApiResponse<Order>>(`/orders/${orderId}`);
    return res.data.data;
  },

  cancel: async (orderId: number): Promise<void> => {
    await apiClient.post(`/orders/${orderId}/cancel`);
  },
};
