import { apiClient, ApiResponse, PageResponse } from '@/api/client';

// ════════════════════════════════════════════════════════════
//  쿠폰 도메인 — CouponController 기반
//   GET  /api/coupons/available        발급 가능 쿠폰 목록
//   POST /api/coupons/{couponId}/issue 쿠폰 발급 (선착순)
//   GET  /api/users/me/coupons         내 쿠폰 목록 (페이징)
// ════════════════════════════════════════════════════════════

export type CouponDiscountType = 'RATE' | 'FIXED';
export type UserCouponStatus = 'AVAILABLE' | 'USED' | 'EXPIRED';

export interface AvailableCoupon {
  couponId: number;
  name: string;
  discountType: CouponDiscountType;
  discountValue: number;
  minOrderPrice: number;
  maxDiscountPrice: number;
  remainingQuantity: number;
  startAt: string;
  expiredAt: string;
}

export interface IssueCouponResult {
  userCouponId: number;
  couponId: number;
  couponName: string;
  status: UserCouponStatus;
  issuedAt: string;
  expiredAt: string;
}

export interface MyCoupon {
  userCouponId: number;
  couponId: number;
  couponName: string;
  discountType: CouponDiscountType;
  discountValue: number;
  minOrderPrice: number;
  maxDiscountPrice: number;
  status: UserCouponStatus;
  issuedAt: string;
  usedAt: string | null;
  expiredAt: string;
}

export interface MyCouponParams {
  page?: number;
  size?: number;
  status?: UserCouponStatus;
}

export const couponApi = {
  // 발급 가능 쿠폰 목록
  getAvailable: async (): Promise<AvailableCoupon[]> => {
    const res = await apiClient.get<ApiResponse<AvailableCoupon[]>>('/coupons/available');
    return res.data.data;
  },

  // 쿠폰 발급 (선착순)
  issue: async (couponId: number): Promise<IssueCouponResult> => {
    const res = await apiClient.post<ApiResponse<IssueCouponResult>>(
      `/coupons/${couponId}/issue`
    );
    return res.data.data;
  },

  // 내 쿠폰 목록 (페이징)
  getMyCoupons: async (params: MyCouponParams = {}): Promise<PageResponse<MyCoupon>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<MyCoupon>>>(
      '/users/me/coupons',
      { params }
    );
    return res.data.data;
  },
};
