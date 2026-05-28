import { apiClient, ApiResponse } from '@/api/client';
import { PaymentMethod } from '@/types/common';

export interface PaymentRequest {
  orderId: number;
  paymentMethod: PaymentMethod;
  amount: number;
  idempotencyKey?: string; // 멱등성 키 (중복 결제 방지)
}

export interface PaymentResponse {
  paymentId: number;
  orderId: number;
  amount: number;
  status: 'APPROVED' | 'FAILED' | 'CANCELLED';
  paidAt: string;
}

export const paymentApi = {
  approve: async (data: PaymentRequest): Promise<PaymentResponse> => {
    // 멱등성 키 자동 생성
    const payload = {
      ...data,
      idempotencyKey: data.idempotencyKey || crypto.randomUUID(),
    };
    const res = await apiClient.post<ApiResponse<PaymentResponse>>('/payments', payload);
    return res.data.data;
  },
};
