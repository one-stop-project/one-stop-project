import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { adminApi } from '@/domains/admin/adminApi';
import { ProductStatus } from '@/types/common';

export function useDashboardQuery() {
  return useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => adminApi.getDashboard(),
    refetchInterval: 60 * 1000,
  });
}

export function useAdminProductsQuery(page = 0, size = 20, status?: ProductStatus) {
  return useQuery({
    queryKey: ['admin', 'products', page, size, status],
    queryFn: () => adminApi.getProducts(page, size, status),
  });
}

export function useAdminSellersQuery(page = 0, size = 20) {
  return useQuery({
    queryKey: ['admin', 'sellers', page, size],
    queryFn: () => adminApi.getSellers(page, size),
  });
}

export function useAdminOrdersQuery(page = 0, size = 20) {
  return useQuery({
    queryKey: ['admin', 'orders', page, size],
    queryFn: () => adminApi.getOrders(page, size),
  });
}

export function useApproveProductMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (productId: number) => adminApi.approveProduct(productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin'] });
      toast.success('승인되었습니다.');
    },
  });
}

export function useRejectProductMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, reason }: { productId: number; reason: string }) =>
      adminApi.rejectProduct(productId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin'] });
      toast.success('반려되었습니다.');
    },
  });
}

export function useApproveSellerMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sellerId: number) => adminApi.approveSeller(sellerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin'] });
      toast.success('판매자가 승인되었습니다.');
    },
  });
}
