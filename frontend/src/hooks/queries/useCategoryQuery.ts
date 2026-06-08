import { useQuery } from '@tanstack/react-query';
import { categoryApi } from '@/domains/category/categoryApi';

const CATEGORY_KEY = ['category'];

// 카테고리 트리 조회 — 거의 안 바뀌므로 길게 캐싱
export function useCategoryTreeQuery() {
  return useQuery({
    queryKey: [...CATEGORY_KEY, 'tree'],
    queryFn: () => categoryApi.getTree(),
    staleTime: 10 * 60 * 1000,   // 10분
    gcTime: 30 * 60 * 1000,
  });
}
