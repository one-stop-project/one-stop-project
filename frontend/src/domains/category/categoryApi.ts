import { apiClient, ApiResponse } from '@/api/client';

// ════════════════════════════════════════════════════════════
//  카테고리 도메인 — CategoryController 기반
//   GET /api/categories   카테고리 트리 조회 (재귀 구조)
// ════════════════════════════════════════════════════════════

export interface CategoryTree {
  id: number;
  name: string;
  children: CategoryTree[];   // 재귀 — 하위 카테고리
}

export const categoryApi = {
  // 카테고리 트리 조회
  getTree: async (): Promise<CategoryTree[]> => {
    const res = await apiClient.get<ApiResponse<CategoryTree[]>>('/categories');
    return res.data.data;
  },
};
