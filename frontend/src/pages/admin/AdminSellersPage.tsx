import { useState } from 'react';
import { Check, X } from 'lucide-react';
import { useAdminSellersQuery, useApproveSellerMutation } from '@/hooks/queries/useAdminQuery';
import { adminApi } from '@/domains/admin/adminApi';
import { useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { PageSpinner } from '@/components/common/Spinner';
import { EmptyState } from '@/components/common/EmptyState';
import { formatDate, formatBusinessNumber } from '@/utils/format';

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-700',
  APPROVED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
  INACTIVE: 'bg-gray-100 text-gray-700',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: '승인 대기',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
  INACTIVE: '비활성',
};

export default function AdminSellersPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useAdminSellersQuery(page, 20);
  const approveMutation = useApproveSellerMutation();
  const queryClient = useQueryClient();

  if (isLoading) return <PageSpinner />;

  const handleReject = async (sellerId: number) => {
    const reason = prompt('반려 사유를 입력하세요');
    if (!reason) return;
    try {
      await adminApi.rejectSeller(sellerId, reason);
      queryClient.invalidateQueries({ queryKey: ['admin'] });
      toast.success('판매자가 반려되었습니다.');
    } catch {
      // 에러는 인터셉터가 처리
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">판매자 관리</h1>

      {data?.content.length === 0 ? (
        <EmptyState title="판매자가 없습니다" />
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">상호명</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">이메일</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">사업자번호</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">상품수</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">등록일</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">상태</th>
                <th className="text-right px-6 py-3 text-xs font-medium text-gray-500 uppercase">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data?.content.map((s) => (
                <tr key={s.sellerId} className="hover:bg-gray-50">
                  <td className="px-6 py-4 text-sm font-medium">{s.shopName}</td>
                  <td className="px-6 py-4 text-sm text-gray-600">{s.email}</td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {formatBusinessNumber(s.businessNumber)}
                  </td>
                  <td className="px-6 py-4 text-sm">{s.productCount}</td>
                  <td className="px-6 py-4 text-sm text-gray-500">{formatDate(s.createdAt)}</td>
                  <td className="px-6 py-4">
                    <span className={`text-xs px-2 py-1 rounded-full ${STATUS_COLORS[s.status]}`}>
                      {STATUS_LABELS[s.status]}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    {s.status === 'PENDING' && (
                      <div className="flex justify-end gap-2">
                        <button
                          onClick={() => approveMutation.mutate(s.sellerId)}
                          className="p-1.5 text-green-600 hover:bg-green-50 rounded"
                          title="승인"
                        >
                          <Check size={16} />
                        </button>
                        <button
                          onClick={() => handleReject(s.sellerId)}
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
