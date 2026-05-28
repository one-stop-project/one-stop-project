import { useState } from 'react';
import { PackagePlus } from 'lucide-react';
import { useSellerProductsQuery, useInboundMutation } from '@/hooks/queries/useSellerQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { EmptyState } from '@/components/common/EmptyState';

export default function SellerInventoryPage() {
  const { data, isLoading } = useSellerProductsQuery(0, 50);
  const inboundMutation = useInboundMutation();

  if (isLoading) return <PageSpinner />;

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">재고 관리</h1>
      <p className="text-sm text-gray-600 mb-6">상품별 재고를 확인하고 입고 처리할 수 있습니다.</p>

      {data?.content.length === 0 ? (
        <EmptyState title="등록된 상품이 없습니다" />
      ) : (
        <div className="space-y-3">
          {data?.content.map((p) => (
            <div key={p.productId} className="card p-5 flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="w-14 h-14 bg-gray-100 rounded-lg shrink-0" />
                <div>
                  <p className="font-medium text-sm">{p.name}</p>
                  <p className="text-xs text-gray-500">{p.categoryName}</p>
                </div>
              </div>

              <div className="flex items-center gap-4">
                <div className="text-right">
                  <p className="text-xs text-gray-500">현재 재고</p>
                  <p className={`text-lg font-bold ${p.totalStock < 10 ? 'text-red-600' : 'text-gray-900'}`}>
                    {p.totalStock.toLocaleString()}
                  </p>
                </div>
                <button
                  onClick={() => {
                    const qty = prompt('입고 수량을 입력하세요');
                    const n = Number(qty);
                    if (n > 0) {
                      // ProductItem 단위 입고 — 여기선 첫 아이템 가정 (실제론 옵션 선택 UI 필요)
                      inboundMutation.mutate({ itemId: p.productId, quantity: n });
                    }
                  }}
                  className="btn-secondary flex items-center gap-1 text-sm"
                >
                  <PackagePlus size={16} />
                  입고
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
