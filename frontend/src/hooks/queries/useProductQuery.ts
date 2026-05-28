import { useQuery } from '@tanstack/react-query';
import { productApi, ProductSearchParams } from '@/domains/product/productApi';

export function useProductListQuery(params: ProductSearchParams = {}) {
  return useQuery({
    queryKey: ['products', 'list', params],
    queryFn: () => productApi.getList(params),
    staleTime: 60 * 1000,
  });
}

export function usePopularProductsQuery(size: number = 8) {
  return useQuery({
    queryKey: ['products', 'popular', size],
    queryFn: () => productApi.getPopular(size),
    staleTime: 5 * 60 * 1000,
  });
}

export function useProductDetailQuery(productId: number | null) {
  return useQuery({
    queryKey: ['products', 'detail', productId],
    queryFn: () => productApi.getDetail(productId!),
    enabled: productId !== null,
    staleTime: 60 * 1000,
  });
}

export function useRelatedProductsQuery(productId: number | null) {
  return useQuery({
    queryKey: ['products', 'related', productId],
    queryFn: () => productApi.getRelated(productId!),
    enabled: productId !== null,
    staleTime: 5 * 60 * 1000,
  });
}

export function useCategoriesQuery() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: () => productApi.getCategories(),
    staleTime: 30 * 60 * 1000, // 카테고리는 거의 안 바뀜
  });
}
