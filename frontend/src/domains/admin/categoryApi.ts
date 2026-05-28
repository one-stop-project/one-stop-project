import { apiClient, ApiResponse } from '@/api/client';
import { Category } from '@/domains/product/productApi';

/**
 * 관리자 카테고리 관리 API
 * 백엔드: AdminCategoryController (/api/admin/categories)
 *
 * 누락 보강: 와이어프레임 adminKeywords.PNG 대응
 */

export interface CategoryCreateRequest {
  name: string;
  parentId?: number | null;
}

export interface CategoryUpdateRequest {
  name: string;
}

export const adminCategoryApi = {
  create: async (data: CategoryCreateRequest): Promise<Category> => {
    const res = await apiClient.post<ApiResponse<Category>>('/admin/categories', data);
    return res.data.data;
  },

  update: async (categoryId: number, data: CategoryUpdateRequest): Promise<Category> => {
    const res = await apiClient.patch<ApiResponse<Category>>(
      `/admin/categories/${categoryId}`,
      data
    );
    return res.data.data;
  },

  delete: async (categoryId: number): Promise<void> => {
    await apiClient.delete(`/admin/categories/${categoryId}`);
  },
};
