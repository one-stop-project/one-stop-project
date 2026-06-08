import { apiClient, ApiResponse } from '@/api/client';

// ════════════════════════════════════════════════════════════
//  구독 도메인 — SubscriptionController 기반
//   POST  /api/subscriptions          구독 시작
//   GET   /api/subscriptions/me        내 구독 조회
//   PATCH /api/subscriptions/cancel    구독 해지
// ════════════════════════════════════════════════════════════

export type SubscriptionStatus = 'ACTIVE' | 'CANCELLED' | 'EXPIRED';

export interface Subscription {
  subscriptionId: number;
  startAt: string;
  endAt: string;
  nextPaymentDate: string;
  status: SubscriptionStatus;
  cancelReason: string | null;
  daysLeft: number;
}

export interface CancelSubscriptionRequest {
  reason: string;            // @NotBlank — 해지 사유 필수
}

export const subscriptionApi = {
  // 구독 시작
  subscribe: async (): Promise<Subscription> => {
    const res = await apiClient.post<ApiResponse<Subscription>>('/subscriptions');
    return res.data.data;
  },

  // 내 구독 조회
  getMySubscription: async (): Promise<Subscription> => {
    const res = await apiClient.get<ApiResponse<Subscription>>('/subscriptions/me');
    return res.data.data;
  },

  // 구독 해지
  cancel: async (data: CancelSubscriptionRequest): Promise<Subscription> => {
    const res = await apiClient.patch<ApiResponse<Subscription>>(
      '/subscriptions/cancel',
      data
    );
    return res.data.data;
  },
};
