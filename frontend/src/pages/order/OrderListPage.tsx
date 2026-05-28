import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Package } from 'lucide-react';
import { useMyOrdersQuery, useCancelOrderMutation } from '@/hooks/queries/useOrderQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { EmptyState } from '@/components/common/EmptyState';
import { formatPrice, formatDateTime } from '@/utils/format';
import { OrderStatus } from '@/types/common';

const STATUS_LABELS: Record<OrderStatus, { text: string; color: string }> = {
  PENDING: { text: '결제 대기', color: 'bg-gray-100 text-gray-700' },
  PAID: { text: '결제 완료', color: 'bg-blue-100 text-blue-700' },
  CONFIRMED: { text: '판매자 확인', color: 'bg-indigo-100 text-indigo-700' },
  SHIPPING: { text: '배송 중', color: 'bg-orange-100 text-orange-700' },
  DELIVERED: { text: '배송 완료', color: 'bg-green-100 text-green-700' },
  CANCELLED: { text: '취소됨', color: 'bg-red-100 text-red-700' },
  REFUNDED: { text: '환불됨', color: 'bg-gray-100 text-gray-700' },
};

export default function OrderListPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useMyOrdersQuery({ page, size: 10 });
  const cancelMutation = useCancelOrderMutation();

  if (isLoading) return <PageSpinner />;

  if (!data?.content.length) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16">
        <EmptyState
          icon={<Package size={40} />}
          title="주문 내역이 없습니다"
          description="첫 주문을 시작해보세요!"
          action={
            <Link to="/products" className="btn-primary inline-block">
              쇼핑하러 가기
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">주문 내역</h1>

      <div className="space-y-4">
        {data.content.map((order) => {
          const statusInfo = STATUS_LABELS[order.status];
          const canCancel = order.status === 'PENDING' || order.status === 'PAID';

          return (
            <div key={order.orderId} className="card p-6">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <p className="text-xs text-gray-500">{formatDateTime(order.createdAt)}</p>
                  <p className="text-sm font-medium text-gray-700">주문번호: {order.orderNumber}</p>
                </div>
                <span
                  className={`px-3 py-1 text-xs font-medium rounded-full ${statusInfo.color}`}
                >
                  {statusInfo.text}
                </span>
              </div>

              <div className="space-y-3">
                {order.items.map((item) => (
                  <div key={item.orderItemId} className="flex gap-3 py-2">
                    <Link
                      to={`/products/${item.productId}`}
                      className="w-16 h-16 bg-gray-100 rounded-lg overflow-hidden shrink-0"
                    >
                      {item.thumbnailUrl ? (
                        <img
                          src={item.thumbnailUrl}
                          alt=""
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <div className="w-full h-full" />
                      )}
                    </Link>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">
                        {item.productName}
                      </p>
                      <p className="text-xs text-gray-500">
                        {item.itemName} · {item.quantity}개
                      </p>
                      <p className="text-sm font-semibold mt-1">{formatPrice(item.subtotal)}</p>
                    </div>
                  </div>
                ))}
              </div>

              <hr className="my-4" />

              <div className="flex items-center justify-between">
                <p className="text-sm text-gray-600">
                  총{' '}
                  <span className="font-bold text-gray-900">{formatPrice(order.finalPrice)}</span>
                </p>
                <div className="flex gap-2">
                  <Link
                    to={`/orders/${order.orderId}`}
                    className="btn-secondary text-sm"
                  >
                    상세 보기
                  </Link>
                  {canCancel && (
                    <button
                      onClick={() => {
                        if (confirm('정말 취소하시겠습니까?')) {
                          cancelMutation.mutate(order.orderId);
                        }
                      }}
                      className="px-4 py-2 text-sm text-primary-600 border border-primary-600 rounded-lg hover:bg-primary-50"
                    >
                      주문 취소
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-8">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={data.first}
            className="btn-secondary text-sm"
          >
            이전
          </button>
          <span className="text-sm text-gray-600">
            {page + 1} / {data.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={data.last}
            className="btn-secondary text-sm"
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}
