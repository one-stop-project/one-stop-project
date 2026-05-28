import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { useSellerProductsQuery, useDeleteProductMutation } from '@/hooks/queries/useSellerQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { EmptyState } from '@/components/common/EmptyState';
import { formatPrice } from '@/utils/format';

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-700',
  ACTIVE: 'bg-green-100 text-green-700',
  INACTIVE: 'bg-gray-100 text-gray-700',
  REJECTED: 'bg-red-100 text-red-700',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: '승인 대기',
  ACTIVE: '판매 중',
  INACTIVE: '판매 중지',
  REJECTED: '반려됨',
};

export default function SellerProductsPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useSellerProductsQuery(page, 20);
  const deleteMutation = useDeleteProductMutation();

  if (isLoading) return <PageSpinner />;

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">상품 관리</h1>
        <Link to="/seller/products/new" className="btn-primary flex items-center gap-2">
          <Plus size={18} />
          상품 등록
        </Link>
      </div>

      {data?.content.length === 0 ? (
        <EmptyState
          title="등록된 상품이 없습니다"
          description="첫 상품을 등록하고 판매를 시작해보세요!"
          action={
            <Link to="/seller/products/new" className="btn-primary inline-block">
              상품 등록하기
            </Link>
          }
        />
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  상품
                </th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  가격
                </th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">
                  재고
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
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className="w-12 h-12 bg-gray-100 rounded shrink-0" />
                      <div>
                        <p className="font-medium text-sm">{p.name}</p>
                        <p className="text-xs text-gray-500">{p.categoryName}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm">{formatPrice(p.price)}</td>
                  <td className="px-6 py-4 text-sm">{p.totalStock.toLocaleString()}</td>
                  <td className="px-6 py-4">
                    <span
                      className={`text-xs px-2 py-1 rounded-full ${STATUS_COLORS[p.status]}`}
                    >
                      {STATUS_LABELS[p.status]}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <button
                      onClick={() => {
                        if (confirm('정말 삭제하시겠습니까?')) {
                          deleteMutation.mutate(p.productId);
                        }
                      }}
                      className="text-sm text-red-600 hover:underline"
                    >
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
