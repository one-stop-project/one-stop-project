import { useState } from 'react';
import { Check, Truck } from 'lucide-react';
import {
  useSellerOrdersQuery,
  useConfirmOrderMutation,
} from '@/hooks/queries/useSellerQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { EmptyState } from '@/components/common/EmptyState';
import { formatPrice, formatDateTime } from '@/utils/format';

export default function SellerOrdersPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useSellerOrdersQuery(page, 20);
  const confirmMutation = useConfirmOrderMutation();

  if (isLoading) return <PageSpinner />;

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">주문 관리</h1>

      {data?.content.length === 0 ? (
        <EmptyState title="들어온 주문이 없습니다" description="상품을 등록하고 판매를 시작해보세요!" />
      ) : (
        <div className="space-y-4">
          {data?.content.map((o) => (
            <div key={o.orderItemId} className="card p-6">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="text-xs text-gray-500">{formatDateTime(o.createdAt)}</p>
                  <p className="text-sm font-medium text-gray-700">주문 #{o.orderId}</p>
                </div>
                <span className="text-xs px-2 py-1 bg-gray-100 rounded-full">{o.status}</span>
              </div>

              <div className="flex items-center gap-4 mb-4">
                <div className="w-14 h-14 bg-gray-100 rounded-lg shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-sm">{o.productName}</p>
                  <p className="text-xs text-gray-500">
                    {o.itemName} · {o.quantity}개
                  </p>
                  <p className="text-sm font-semibold mt-1">{formatPrice(o.price * o.quantity)}</p>
                </div>
              </div>

              <div className="bg-gray-50 rounded-lg p-3 text-sm mb-4">
                <p className="text-gray-600">
                  받는 분: <span className="font-medium text-gray-900">{o.receiverName}</span>
                </p>
                <p className="text-gray-600 mt-1">
                  주소: <span className="font-medium text-gray-900">{o.shippingAddress}</span>
                </p>
              </div>

              <div className="flex gap-2">
                {o.status === 'PAID' && (
                  <button
                    onClick={() => confirmMutation.mutate(o.orderItemId)}
                    className="btn-primary flex items-center gap-1 text-sm"
                  >
                    <Check size={16} />
                    주문 확인
                  </button>
                )}
                {o.status === 'CONFIRMED' && (
                  <button className="btn-secondary flex items-center gap-1 text-sm">
                    <Truck size={16} />
                    배송 시작
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-6">
          <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={data.first} className="btn-secondary text-sm">
            이전
          </button>
          <span className="text-sm text-gray-600">{page + 1} / {data.totalPages}</span>
          <button onClick={() => setPage((p) => p + 1)} disabled={data.last} className="btn-secondary text-sm">
            다음
          </button>
        </div>
      )}
    </div>
  );
}
