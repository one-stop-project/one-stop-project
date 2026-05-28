import { useParams, Link } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { useOrderDetailQuery } from '@/hooks/queries/useOrderQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { formatPrice } from '@/utils/format';

export default function OrderCompletePage() {
  const { id } = useParams<{ id: string }>();
  const orderId = id ? Number(id) : null;

  const { data: order, isLoading } = useOrderDetailQuery(orderId);

  if (isLoading || !order) return <PageSpinner />;

  return (
    <div className="max-w-2xl mx-auto px-4 py-12">
      <div className="card p-8 text-center animate-fade-in">
        <div className="w-16 h-16 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto mb-4">
          <CheckCircle2 size={36} />
        </div>

        <h1 className="text-2xl font-bold text-gray-900 mb-2">주문이 완료되었습니다!</h1>
        <p className="text-sm text-gray-600 mb-6">
          소중한 주문 감사합니다. 빠르고 안전하게 배송해드릴게요.
        </p>

        <div className="bg-gray-50 rounded-lg p-4 text-sm text-left mb-6">
          <div className="flex justify-between py-1">
            <span className="text-gray-600">주문번호</span>
            <span className="font-medium">{order.orderNumber}</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-gray-600">결제 금액</span>
            <span className="font-bold text-primary-600">{formatPrice(order.finalPrice)}</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-gray-600">배송지</span>
            <span className="font-medium text-right">{order.shippingAddress}</span>
          </div>
        </div>

        <div className="flex gap-3">
          <Link to={`/orders/${order.orderId}`} className="btn-secondary flex-1">
            주문 상세
          </Link>
          <Link to="/" className="btn-primary flex-1">
            계속 쇼핑하기
          </Link>
        </div>
      </div>
    </div>
  );
}
