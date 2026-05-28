import { useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';
import { paymentApi, PaymentRequest } from '@/domains/payment/paymentApi';

export function useApprovePaymentMutation() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: PaymentRequest) => paymentApi.approve(data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      toast.success('결제가 완료되었습니다.');
      navigate(`/orders/${data.orderId}/complete`);
    },
  });
}
