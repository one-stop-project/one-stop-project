import { useQuery } from '@tanstack/react-query';
import { deliveryApi } from '@/domains/delivery/deliveryApi';

export function useOrderDeliveriesQuery(orderId: number | null) {
  return useQuery({
    queryKey: ['deliveries', 'order', orderId],
    queryFn: () => deliveryApi.getByOrder(orderId!),
    enabled: orderId !== null,
  });
}

export function useDeliveryHistoryQuery(deliveryId: number | null) {
  return useQuery({
    queryKey: ['deliveries', 'history', deliveryId],
    queryFn: () => deliveryApi.getHistory(deliveryId!),
    enabled: deliveryId !== null,
  });
}
