import { apiClient, ApiResponse, PageResponse } from '@/api/client';
import { OrderStatus, PaymentMethod } from '@/types/common';

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
  shippingAddress: string;
  paymentMethod?: PaymentMethod;
  createdAt: string;
}

export interface CreateOrderRequest {
  items: Array<{
    productItemId: number;
    quantity: number;
  }>;
  receiverName: string;
  receiverPhone: string;
  shippingAddress: string;
  message?: string;
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
