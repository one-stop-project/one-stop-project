import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  reviewApi,
  CreateReviewRequest,
  UpdateReviewRequest,
  MyReviewParams,
} from '@/domains/review/reviewApi';
import { useAuthStore } from '@/store/useAuthStore';

const REVIEW_KEY = ['review'];

// 내 리뷰 목록
export function useMyReviewsQuery(params: MyReviewParams = {}) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: [...REVIEW_KEY, 'me', params],
    queryFn: () => reviewApi.getMyReviews(params),
    enabled: isAuthenticated,
  });
}

// 작성 가능한 리뷰 목록
export function useReviewableQuery() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: [...REVIEW_KEY, 'reviewable'],
    queryFn: () => reviewApi.getReviewable(),
    enabled: isAuthenticated,
  });
}

// 상품별 AI 리뷰 요약
export function useAiReviewSummaryQuery(productId: number, enabled = true) {
  return useQuery({
    queryKey: [...REVIEW_KEY, 'ai-summary', productId],
    queryFn: () => reviewApi.getAiSummary(productId),
    enabled: enabled && !!productId,
    staleTime: 5 * 60 * 1000, // AI 요약은 자주 안 바뀜
    retry: false,             // 리뷰 10개 미만이면 없을 수 있으니 재시도 안 함
  });
}

// 리뷰 작성
export function useCreateReviewMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateReviewRequest) => reviewApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...REVIEW_KEY, 'me'] });
      queryClient.invalidateQueries({ queryKey: [...REVIEW_KEY, 'reviewable'] });
      toast.success('리뷰가 등록되었습니다.');
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.message ?? '리뷰 등록에 실패했습니다.');
    },
  });
}

// 리뷰 수정
export function useUpdateReviewMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ reviewId, data }: { reviewId: number; data: UpdateReviewRequest }) =>
      reviewApi.update(reviewId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...REVIEW_KEY, 'me'] });
      toast.success('리뷰가 수정되었습니다.');
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.message ?? '리뷰 수정에 실패했습니다.');
    },
  });
}

// 리뷰 삭제
export function useDeleteReviewMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (reviewId: number) => reviewApi.remove(reviewId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...REVIEW_KEY, 'me'] });
      queryClient.invalidateQueries({ queryKey: [...REVIEW_KEY, 'reviewable'] });
      toast.success('리뷰가 삭제되었습니다.');
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.message ?? '리뷰 삭제에 실패했습니다.');
    },
  });
}
