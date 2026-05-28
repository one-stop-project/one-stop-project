import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';
import { orderApi, CreateOrderRequest, OrderListParams } from '@/domains/order/orderApi';

export function useMyOrdersQuery(params: OrderListParams = {}) {
  return useQuery({
    queryKey: ['orders', 'my', params],
    queryFn: () => orderApi.getMyOrders(params),
    staleTime: 30 * 1000,
  });
}

export function useOrderDetailQuery(orderId: number | null) {
  return useQuery({
    queryKey: ['orders', 'detail', orderId],
    queryFn: () => orderApi.getDetail(orderId!),
    enabled: orderId !== null,
  });
}

export function useCreateOrderMutation() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateOrderRequest) => orderApi.create(data),
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      navigate(`/payment/${order.orderId}`);
    },
  });
}

export function useCancelOrderMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (orderId: number) => orderApi.cancel(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      toast.success('주문이 취소되었습니다.');
    },
  });
}
