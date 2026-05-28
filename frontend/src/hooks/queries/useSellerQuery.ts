import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { sellerApi, ProductCreateRequest, ProductUpdateRequest } from '@/domains/seller/sellerApi';

export function useSellerProductsQuery(page = 0, size = 20) {
  return useQuery({
    queryKey: ['seller', 'products', page, size],
    queryFn: () => sellerApi.getMyProducts(page, size),
  });
}

export function useSellerOrdersQuery(page = 0, size = 20) {
  return useQuery({
    queryKey: ['seller', 'orders', page, size],
    queryFn: () => sellerApi.getMyOrders(page, size),
  });
}

export function useCreateProductMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: ProductCreateRequest) => sellerApi.createProduct(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller', 'products'] });
      toast.success('상품이 등록되었습니다. 관리자 승인 후 노출됩니다.');
    },
  });
}

export function useUpdateProductMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, data }: { productId: number; data: ProductUpdateRequest }) =>
      sellerApi.updateProduct(productId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller', 'products'] });
      toast.success('상품이 수정되었습니다.');
    },
  });
}

export function useDeleteProductMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (productId: number) => sellerApi.deleteProduct(productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller', 'products'] });
      toast.success('삭제되었습니다.');
    },
  });
}

export function useInboundMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      sellerApi.inbound(itemId, quantity),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller'] });
      toast.success('재고가 입고되었습니다.');
    },
  });
}

export function useConfirmOrderMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderItemId: number) => sellerApi.confirmOrder(orderItemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller', 'orders'] });
      toast.success('주문이 확인되었습니다.');
    },
  });
}

export function useShipDeliveryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      deliveryId,
      carrierName,
      trackingNumber,
    }: {
      deliveryId: number;
      carrierName: string;
      trackingNumber: string;
    }) => sellerApi.shipDelivery(deliveryId, { carrierName, trackingNumber }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller'] });
      toast.success('배송이 시작되었습니다.');
    },
  });
}
