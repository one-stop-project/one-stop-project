import { apiClient, ApiResponse } from '@/api/client';
import { DeliveryStatus } from '@/types/common';

export interface Delivery {
  deliveryId: number;
  orderId: number;
  orderItemId: number;
  status: DeliveryStatus;
  carrierName: string | null;
  trackingNumber: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  receiverName: string;
  shippingAddress: string;
}

export interface DeliveryHistory {
  historyId: number;
  status: DeliveryStatus;
  location: string;
  message: string;
  occurredAt: string;
}

export const deliveryApi = {
  getByOrder: async (orderId: number): Promise<Delivery[]> => {
    const res = await apiClient.get<ApiResponse<Delivery[]>>(`/orders/${orderId}/deliveries`);
    return res.data.data;
  },

  getHistory: async (deliveryId: number): Promise<DeliveryHistory[]> => {
    const res = await apiClient.get<ApiResponse<DeliveryHistory[]>>(
      `/deliveries/${deliveryId}/history`
    );
    return res.data.data;
  },
};
