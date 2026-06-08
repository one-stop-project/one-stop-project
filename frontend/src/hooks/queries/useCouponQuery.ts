import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { couponApi, MyCouponParams } from '@/domains/coupon/couponApi';
import { useAuthStore } from '@/store/useAuthStore';

const COUPON_KEY = ['coupon'];

// 발급 가능 쿠폰 목록
export function useAvailableCouponsQuery() {
  return useQuery({
    queryKey: [...COUPON_KEY, 'available'],
    queryFn: () => couponApi.getAvailable(),
    staleTime: 30 * 1000,
  });
}

// 내 쿠폰 목록
export function useMyCouponsQuery(params: MyCouponParams = {}) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: [...COUPON_KEY, 'me', params],
    queryFn: () => couponApi.getMyCoupons(params),
    enabled: isAuthenticated,
  });
}

// 쿠폰 발급 (선착순)
export function useIssueCouponMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (couponId: number) => couponApi.issue(couponId),
    onSuccess: () => {
      // 발급 가능 목록(잔여 수량) + 내 쿠폰 목록 갱신
      queryClient.invalidateQueries({ queryKey: [...COUPON_KEY, 'available'] });
      queryClient.invalidateQueries({ queryKey: [...COUPON_KEY, 'me'] });
      toast.success('쿠폰이 발급되었습니다.');
    },
    onError: (error: any) => {
      // 선착순 소진 / 이미 발급 등 — 백엔드 메시지 우선 노출
      const message =
        error?.response?.data?.message ?? '쿠폰 발급에 실패했습니다.';
      toast.error(message);
    },
  });
}
