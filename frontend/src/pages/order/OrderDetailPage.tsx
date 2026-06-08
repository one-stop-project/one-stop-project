import {Link, useParams} from 'react-router-dom';
import {MapPin, Package, Phone, Truck} from 'lucide-react';
import {useCancelOrderMutation, useOrderDetailQuery} from '@/hooks/queries/useOrderQuery';
import {useOrderDeliveriesQuery} from '@/hooks/queries/useDeliveryQuery';
import {PageSpinner} from '@/components/common/Spinner';
import {formatDateTime, formatPrice} from '@/utils/format';
import {DeliveryStatus, OrderStatus} from '@/types/common';

const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  CONFIRMED: '판매자 확인',
  SHIPPING: '배송 중',
  DELIVERED: '배송 완료',
  CANCELLED: '취소됨',
  REFUNDED: '환불됨',
};

const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  PREPARING: '배송 준비 중',
  SHIPPED: '발송됨',
  IN_TRANSIT: '배송 중',
  DELIVERED: '배송 완료',
};

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const orderId = id ? Number(id) : null;

  const { data: order, isLoading } = useOrderDetailQuery(orderId);
  const { data: deliveries } = useOrderDeliveriesQuery(orderId);
  const cancelMutation = useCancelOrderMutation();

  if (isLoading || !order) return <PageSpinner />;

  const canCancel = order.status === 'PENDING' || order.status === 'PAID';

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">주문 상세</h1>
        <span className="text-sm px-3 py-1 bg-primary-50 text-primary-700 rounded-full font-medium">
          {ORDER_STATUS_LABELS[order.status]}
        </span>
      </div>

      {/* 주문 정보 */}
      <section className="card p-6 mb-4">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Package size={20} className="text-primary-600" />
            주문 상품
          </h2>
          <p className="text-xs text-gray-500">{formatDateTime(order.createdAt)}</p>
        </div>

        <div className="space-y-3">
          {order.items.map((item) => (
            <div key={item.orderItemId} className="flex gap-3 py-2">
              <Link
                to={`/products/${item.productId}`}
                className="w-16 h-16 bg-gray-100 rounded-lg overflow-hidden shrink-0"
              >
                {item.thumbnailUrl && (
                  <img src={item.thumbnailUrl} alt="" className="w-full h-full object-cover" />
                )}
              </Link>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">{item.productName}</p>
                <p className="text-xs text-gray-500">
                  {item.itemName} · {item.quantity}개
                </p>
                <p className="text-xs text-gray-400">{item.sellerName}</p>
              </div>
              <p className="text-sm font-semibold">{formatPrice(item.subtotal)}</p>
            </div>
          ))}
        </div>
      </section>

      {/* 배송 정보 */}
      <section className="card p-6 mb-4">
        <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
          <MapPin size={20} className="text-primary-600" />
          배송지 정보
        </h2>
        <div className="space-y-2 text-sm">
          <p className="flex items-center gap-2">
            <span className="text-gray-500 w-16">받는 분</span>
            <span className="font-medium">{order.receiverName}</span>
          </p>
          <p className="flex items-center gap-2">
            <Phone size={14} className="text-gray-400" />
            <span className="font-medium">{order.receiverPhone}</span>
          </p>
          <p className="flex items-start gap-2">
            <span className="text-gray-500 w-16">주소</span>
            <span className="font-medium">{order.receiverAddress}</span>
          </p>
        </div>
      </section>

      {/* 배송 추적 */}
      {deliveries && deliveries.length > 0 && (
        <section className="card p-6 mb-4">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <Truck size={20} className="text-primary-600" />
            배송 추적
          </h2>
          <div className="space-y-3">
            {deliveries.map((d) => (
              <div key={d.deliveryId} className="border border-gray-100 rounded-lg p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-medium">
                    {DELIVERY_STATUS_LABELS[d.status]}
                  </span>
                  {d.trackingNumber && (
                    <span className="text-xs text-gray-500">
                      {d.carrierName} {d.trackingNumber}
                    </span>
                  )}
                </div>
                {d.shippedAt && (
                  <p className="text-xs text-gray-500">발송: {formatDateTime(d.shippedAt)}</p>
                )}
                {d.deliveredAt && (
                  <p className="text-xs text-gray-500">완료: {formatDateTime(d.deliveredAt)}</p>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      {/* 결제 요약 */}
      <section className="card p-6 mb-6">
        <h2 className="text-lg font-semibold mb-4">결제 정보</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-gray-600">상품 금액</span>
            <span>{formatPrice(order.totalPrice)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">배송비</span>
            <span>{order.shippingFee === 0 ? '무료' : formatPrice(order.shippingFee)}</span>
          </div>
          <hr className="my-2" />
          <div className="flex justify-between text-base font-semibold">
            <span>총 결제 금액</span>
            <span className="text-primary-600">{formatPrice(order.finalPrice)}</span>
          </div>
        </div>
      </section>

      {/* 액션 */}
      <div className="flex gap-3">
        <Link to="/orders" className="btn-secondary flex-1 text-center">
          목록으로
        </Link>
        {canCancel && (
          <button
            onClick={() => {
              if (confirm('정말 취소하시겠습니까?')) cancelMutation.mutate(order.orderId);
            }}
            className="flex-1 px-4 py-2 text-primary-600 border border-primary-600 rounded-lg hover:bg-primary-50 font-medium"
          >
            주문 취소
          </button>
        )}
      </div>
    </div>
  );
}
