import { useState } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { CreditCard, Building2 } from 'lucide-react';
import { useOrderDetailQuery } from '@/hooks/queries/useOrderQuery';
import { useApprovePaymentMutation } from '@/hooks/queries/usePaymentQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { formatPrice } from '@/utils/format';
import { PaymentMethod } from '@/types/common';

const PAYMENT_METHODS: { value: PaymentMethod; label: string; icon: any }[] = [
  { value: 'CARD', label: '신용/체크카드', icon: CreditCard },
  { value: 'BANK_TRANSFER', label: '계좌이체', icon: Building2 },
  { value: 'KAKAO_PAY', label: '카카오페이', icon: () => <span className="text-yellow-500 font-bold">K</span> },
  { value: 'TOSS_PAY', label: '토스페이', icon: () => <span className="text-blue-500 font-bold">T</span> },
];

export default function PaymentPage() {
  const { id } = useParams<{ id: string }>();
  const orderId = id ? Number(id) : null;

  const { data: order, isLoading } = useOrderDetailQuery(orderId);
  const approveMutation = useApprovePaymentMutation();
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod>('CARD');

  if (isLoading || !order) return <PageSpinner />;

  if (order.status !== 'PENDING') {
    return <Navigate to={`/orders/${orderId}`} replace />;
  }

  const handlePay = () => {
    approveMutation.mutate({
      orderId: order.orderId,
      amount: order.finalPrice,
      paymentMethod: selectedMethod,
    });
  };

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">결제하기</h1>

      {/* 주문 정보 */}
      <section className="card p-6 mb-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">주문 정보</h2>
        <div className="space-y-3 text-sm">
          {order.items.map((item) => (
            <div key={item.orderItemId} className="flex justify-between">
              <span className="text-gray-700">
                {item.productName} ({item.itemName}) × {item.quantity}
              </span>
              <span className="font-medium">{formatPrice(item.subtotal)}</span>
            </div>
          ))}
          <hr />
          <div className="flex justify-between">
            <span className="text-gray-600">상품 금액</span>
            <span>{formatPrice(order.totalPrice)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">배송비</span>
            <span>{order.shippingFee === 0 ? '무료' : formatPrice(order.shippingFee)}</span>
          </div>
          <hr />
          <div className="flex justify-between text-base font-semibold">
            <span>총 결제 금액</span>
            <span className="text-primary-600">{formatPrice(order.finalPrice)}</span>
          </div>
        </div>
      </section>

      {/* 결제 수단 */}
      <section className="card p-6 mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">결제 수단 선택</h2>
        <div className="grid grid-cols-2 gap-3">
          {PAYMENT_METHODS.map(({ value, label, icon: Icon }) => (
            <button
              key={value}
              onClick={() => setSelectedMethod(value)}
              className={`p-4 rounded-lg border-2 transition-all flex items-center gap-3 ${
                selectedMethod === value
                  ? 'border-primary-600 bg-primary-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <Icon className="w-5 h-5" />
              <span className="text-sm font-medium">{label}</span>
            </button>
          ))}
        </div>
      </section>

      <button
        onClick={handlePay}
        disabled={approveMutation.isPending}
        className="btn-primary w-full py-4 text-lg"
      >
        {approveMutation.isPending
          ? '결제 처리 중...'
          : `${formatPrice(order.finalPrice)} 결제하기`}
      </button>
    </div>
  );
}
