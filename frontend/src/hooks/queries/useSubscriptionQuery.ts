import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  subscriptionApi,
  CancelSubscriptionRequest,
} from '@/domains/subscription/subscriptionApi';
import { useAuthStore } from '@/store/useAuthStore';

const SUBSCRIPTION_KEY = ['subscription'];

// 내 구독 조회
export function useMySubscriptionQuery() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: [...SUBSCRIPTION_KEY, 'me'],
    queryFn: () => subscriptionApi.getMySubscription(),
    enabled: isAuthenticated,
    retry: false,   // 구독 없으면 404일 수 있으니 재시도 안 함
  });
}

// 구독 시작
export function useSubscribeMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => subscriptionApi.subscribe(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...SUBSCRIPTION_KEY, 'me'] });
      toast.success('구독이 시작되었습니다.');
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.message ?? '구독 시작에 실패했습니다.');
    },
  });
}

// 구독 해지
export function useCancelSubscriptionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CancelSubscriptionRequest) => subscriptionApi.cancel(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...SUBSCRIPTION_KEY, 'me'] });
      toast.success('구독이 해지되었습니다.');
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.message ?? '구독 해지에 실패했습니다.');
    },
  });
}
