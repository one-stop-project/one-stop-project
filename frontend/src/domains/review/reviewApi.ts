import { apiClient, ApiResponse, PageResponse } from '@/api/client';

// ════════════════════════════════════════════════════════════
//  리뷰 도메인 — ReviewController + AiReviewSummaryController 기반
//   POST   /api/reviews                            리뷰 작성
//   PATCH  /api/reviews/{reviewId}                 리뷰 수정
//   DELETE /api/reviews/{reviewId}                 리뷰 삭제
//   GET    /api/reviews/me                         내 리뷰 목록 (페이징)
//   GET    /api/reviews/reviewable                 작성 가능한 리뷰 목록
//   GET    /api/products/{productId}/reviews/ai-summary  AI 리뷰 요약
// ════════════════════════════════════════════════════════════

export interface Review {
  reviewId: number;
  productId: number;
  orderItemId: number;
  rating: number;
  content: string;
  imageUrls: string[];
  createdAt: string;
  updatedAt: string;
}

export interface MyReview {
  reviewId: number;
  productId: number;
  productName: string;
  rating: number;
  content: string;
  createdAt: string;
}

// 상품 상세에 노출되는 리뷰 (작성자명 포함)
export interface ProductReview {
  reviewId: number;
  reviewerName: string;
  rating: number;
  content: string;
  imageUrls: string[];
  createdAt: string;
}

// 아직 리뷰를 쓰지 않은, 작성 가능한 주문 상품
export interface ReviewableOrderItem {
  orderItemId: number;
  productId: number;
  productName: string;
  optionName: string;
  deliveredAt: string;
}

export interface CreateReviewRequest {
  orderItemId: number;       // @NotNull
  rating: number;            // 1~5
  content: string;           // 10~1000자
  imageUrls?: string[];      // 최대 5장
}

export interface UpdateReviewRequest {
  rating: number;            // 1~5
  content: string;           // 10~1000자
  retainedImageUrls?: string[];  // 유지할 기존 이미지
  newImageUrls?: string[];       // 새로 추가할 이미지
}

// AI 리뷰 요약
export interface AiReviewSummary {
  reviewCount: number;
  averageRating: number;
  summary: {
    positive?: string;
    negative?: string;
    keywords?: string[];
  } | null;
}

export interface MyReviewParams {
  page?: number;
  size?: number;
}

export const reviewApi = {
  create: async (data: CreateReviewRequest): Promise<Review> => {
    const res = await apiClient.post<ApiResponse<Review>>('/reviews', data);
    return res.data.data;
  },

  update: async (reviewId: number, data: UpdateReviewRequest): Promise<Review> => {
    const res = await apiClient.patch<ApiResponse<Review>>(`/reviews/${reviewId}`, data);
    return res.data.data;
  },

  remove: async (reviewId: number): Promise<void> => {
    await apiClient.delete(`/reviews/${reviewId}`);
  },

  getMyReviews: async (params: MyReviewParams = {}): Promise<PageResponse<Review>> => {
    const res = await apiClient.get<ApiResponse<PageResponse<Review>>>('/reviews/me', {
      params,
    });
    return res.data.data;
  },

  // 작성 가능한 리뷰 목록 (배송완료 + 미작성)
  getReviewable: async (): Promise<ReviewableOrderItem[]> => {
    const res = await apiClient.get<ApiResponse<ReviewableOrderItem[]>>(
      '/reviews/reviewable'
    );
    return res.data.data;
  },

  // 상품별 AI 리뷰 요약 (리뷰 10개 이상 시 제공)
  getAiSummary: async (productId: number): Promise<AiReviewSummary> => {
    const res = await apiClient.get<ApiResponse<AiReviewSummary>>(
      `/products/${productId}/reviews/ai-summary`
    );
    return res.data.data;
  },
};
