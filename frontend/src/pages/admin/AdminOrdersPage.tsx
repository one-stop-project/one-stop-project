import { useState } from 'react';
import { useAdminOrdersQuery } from '@/hooks/queries/useAdminQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { EmptyState } from '@/components/common/EmptyState';
import { formatPrice, formatDateTime } from '@/utils/format';

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-700',
  PAID: 'bg-blue-100 text-blue-700',
  CONFIRMED: 'bg-indigo-100 text-indigo-700',
  SHIPPING: 'bg-orange-100 text-orange-700',
  DELIVERED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-700',
  REFUNDED: 'bg-gray-100 text-gray-700',
};

export default function AdminOrdersPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useAdminOrdersQuery(page, 20);

  if (isLoading) return <PageSpinner />;

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">전체 주문 조회</h1>

      {data?.content.length === 0 ? (
        <EmptyState title="주문이 없습니다" />
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">주문번호</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">구매자</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">금액</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">주문일시</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">상태</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data?.content.map((o) => (
                <tr key={o.orderId} className="hover:bg-gray-50">
                  <td className="px-6 py-4 text-sm font-medium">{o.orderNumber}</td>
                  <td className="px-6 py-4 text-sm text-gray-600">{o.buyerName}</td>
                  <td className="px-6 py-4 text-sm font-semibold">{formatPrice(o.finalPrice)}</td>
                  <td className="px-6 py-4 text-sm text-gray-500">{formatDateTime(o.createdAt)}</td>
                  <td className="px-6 py-4">
                    <span className={`text-xs px-2 py-1 rounded-full ${STATUS_COLORS[o.status] || 'bg-gray-100 text-gray-700'}`}>
                      {o.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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
