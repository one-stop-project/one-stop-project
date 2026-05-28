import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Check, X } from 'lucide-react';
import {
  useAdminProductsQuery,
  useApproveProductMutation,
  useRejectProductMutation,
} from '@/hooks/queries/useAdminQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { EmptyState } from '@/components/common/EmptyState';
import { formatPrice, formatDate } from '@/utils/format';
import { ProductStatus } from '@/types/common';

const STATUS_FILTERS: { value: ProductStatus | undefined; label: string }[] = [
  { value: undefined, label: '전체' },
  { value: 'PENDING', label: '승인 대기' },
  { value: 'ACTIVE', label: '판매 중' },
  { value: 'INACTIVE', label: '판매 중지' },
  { value: 'REJECTED', label: '반려' },
];

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-700',
  ACTIVE: 'bg-green-100 text-green-700',
  INACTIVE: 'bg-gray-100 text-gray-700',
  REJECTED: 'bg-red-100 text-red-700',
};

export default function AdminProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(0);

  const statusParam = searchParams.get('status') as ProductStatus | null;
  const { data, isLoading } = useAdminProductsQuery(page, 20, statusParam || undefined);
  const approveMutation = useApproveProductMutation();
  const rejectMutation = useRejectProductMutation();

  if (isLoading) return <PageSpinner />;

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">상품 관리</h1>

      {/* 필터 */}
      <div className="flex items-center gap-2 mb-4">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.label}
            onClick={() => {
              const next = new URLSearchParams(searchParams);
              if (f.value) next.set('status', f.value);
              else next.delete('status');
              setSearchParams(next);
              setPage(0);
            }}
            className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
              statusParam === f.value || (!statusParam && !f.value)
                ? 'bg-gray-900 text-white'
                : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {data?.content.length === 0 ? (
        <EmptyState title="상품이 없습니다" />
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  상품명
                </th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  판매자
                </th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  카테고리
                </th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  가격
                </th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  등록일
                </th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  상태
                </th>
                <th className="text-right px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  관리
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data?.content.map((p) => (
                <tr key={p.productId} className="hover:bg-gray-50">
                  <td className="px-6 py-4 text-sm font-medium">{p.name}</td>
                  <td className="px-6 py-4 text-sm text-gray-600">{p.sellerName}</td>
                  <td className="px-6 py-4 text-sm text-gray-600">{p.categoryName}</td>
                  <td className="px-6 py-4 text-sm">{formatPrice(p.price)}</td>
                  <td className="px-6 py-4 text-sm text-gray-500">{formatDate(p.createdAt)}</td>
                  <td className="px-6 py-4">
                    <span className={`text-xs px-2 py-1 rounded-full ${STATUS_COLORS[p.status]}`}>
                      {p.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    {p.status === 'PENDING' && (
                      <div className="flex justify-end gap-2">
                        <button
                          onClick={() => approveMutation.mutate(p.productId)}
                          className="p-1.5 text-green-600 hover:bg-green-50 rounded"
                          title="승인"
                        >
                          <Check size={16} />
                        </button>
                        <button
                          onClick={() => {
                            const reason = prompt('반려 사유를 입력하세요');
                            if (reason)
                              rejectMutation.mutate({ productId: p.productId, reason });
                          }}
                          className="p-1.5 text-red-600 hover:bg-red-50 rounded"
                          title="반려"
                        >
                          <X size={16} />
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 페이지네이션 */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-6">
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
