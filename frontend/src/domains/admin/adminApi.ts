import { apiClient, ApiResponse, PageResponse } from '@/api/client';
import { ProductStatus, SellerStatus } from '@/types/common';

export interface DashboardStats {
  totalUsers: number;
  totalSellers: number;
  totalProducts: number;
  pendingProducts: number;
  pendingSellers: number;
  todayOrders: number;
  todayRevenue: number;
}

export interface AdminProduct {
  productId: number;
  name: string;
  sellerName: string;
  categoryName: string;
  price: number;
  status: ProductStatus;
  createdAt: string;
}

export interface AdminSeller {
  sellerId: number;
  shopName: string;
  email: string;
  businessNumber: string;
  status: SellerStatus;
  productCount: number;
  createdAt: string;
}

export interface AdminOrder {
  orderId: number;
  orderNumber: string;
  buyerName: string;
  finalPrice: number;
  status: string;
  createdAt: string;
}

export const adminApi = {
  getDashboard: async (): Promise<DashboardStats> => {
    const res = await apiClient.get<ApiResponse<DashboardStats>>('/admin/dashboard');
    return res.data.data;
  },

  // 상품 관리
  getProducts: async (
    page = 0,
    size = 20,
    status?: ProductStatus
  ): Promise<PageResponse<AdminProduct>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<AdminProduct>>>('/admin/products', {
      params: { page, size, status },
    });
    return res.data.data;
  },

  approveProduct: async (productId: number): Promise<void> => {
    await apiClient.patch(`/admin/products/${productId}/approve`);
  },

  rejectProduct: async (productId: number, reason: string): Promise<void> => {
    await apiClient.patch(`/admin/products/${productId}/reject`, { reason });
  },

  forceInactiveProduct: async (productId: number, reason: string): Promise<void> => {
    await apiClient.patch(`/admin/products/${productId}/force-inactive`, { reason });
  },

  // 판매자 관리
  getSellers: async (page = 0, size = 20): Promise<PageResponse<AdminSeller>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<AdminSeller>>>('/admin/sellers', {
      params: { page, size },
    });
    return res.data.data;
  },

  approveSeller: async (sellerId: number): Promise<void> => {
    await apiClient.patch(`/admin/sellers/${sellerId}/approve`);
  },

  rejectSeller: async (sellerId: number, reason: string): Promise<void> => {
    await apiClient.patch(`/admin/sellers/${sellerId}/reject`, { reason });
  },

  forceInactiveSeller: async (sellerId: number, reason: string): Promise<void> => {
    await apiClient.patch(`/admin/sellers/${sellerId}/force-inactive`, { reason });
  },

  // 주문 관리
  getOrders: async (page = 0, size = 20): Promise<PageResponse<AdminOrder>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<AdminOrder>>>('/admin/orders', {
      params: { page, size },
    });
    return res.data.data;
  },
};
