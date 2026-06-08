import { apiClient, ApiResponse } from '@/api/client';

// ════════════════════════════════════════════════════════════
//  AI 도메인 — AiShoppingAssistantController 기반
//   POST /api/ai/assistant   AI 쇼핑 어시스턴트 질의
//
//  (AI 리뷰 요약은 reviewApi.getAiSummary 에 포함)
// ════════════════════════════════════════════════════════════

export interface ShoppingAssistantRequest {
  message: string;           // @NotBlank, 최대 500자
}

export interface ShoppingAssistantResponse {
  answer: string;
}

export const aiApi = {
  // AI 쇼핑 어시스턴트에게 질문
  ask: async (message: string): Promise<ShoppingAssistantResponse> => {
    const res = await apiClient.post<ApiResponse<ShoppingAssistantResponse>>(
      '/ai/assistant',
      { message }
    );
    return res.data.data;
  },
};
