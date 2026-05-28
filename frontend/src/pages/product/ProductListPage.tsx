import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useProductListQuery, useCategoriesQuery } from '@/hooks/queries/useProductQuery';
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { EmptyState } from '@/components/common/EmptyState';

type SortOption = 'id,desc' | 'salesCount,desc' | 'viewCount,desc';

const SORT_LABELS: Record<SortOption, string> = {
  'id,desc': '최신순',
  'salesCount,desc': '인기순',
  'viewCount,desc': '조회순',
};

export default function ProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(0);

  const keyword = searchParams.get('keyword') || undefined;
  const categoryIdStr = searchParams.get('categoryId');
  const categoryId = categoryIdStr ? Number(categoryIdStr) : undefined;
  const sort = (searchParams.get('sort') as SortOption) || 'id,desc';

  const { data: categories } = useCategoriesQuery();
  const { data, isLoading } = useProductListQuery({
    page,
    size: 20,
    keyword,
    categoryId,
    sort,
  });

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    setSearchParams(next);
    setPage(0);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* 헤더 */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">
          {keyword ? `"${keyword}" 검색 결과` : '전체 상품'}
        </h1>
        {data && <p className="text-sm text-gray-600">총 {data.totalElements.toLocaleString()}개</p>}
      </div>

      <div className="flex gap-8">
        {/* 사이드바 — 카테고리 */}
        <aside className="hidden md:block w-56 shrink-0">
          <div className="sticky top-32">
            <h3 className="text-sm font-semibold text-gray-900 mb-3">카테고리</h3>
            <ul className="space-y-1">
              <li>
                <button
                  onClick={() => updateParam('categoryId', '')}
                  className={`w-full text-left px-3 py-2 text-sm rounded-lg transition-colors ${
                    !categoryId
                      ? 'bg-primary-50 text-primary-700 font-medium'
                      : 'text-gray-700 hover:bg-gray-100'
                  }`}
                >
                  전체
                </button>
              </li>
              {categories?.map((cat) => (
                <li key={cat.categoryId}>
                  <button
                    onClick={() => updateParam('categoryId', String(cat.categoryId))}
                    className={`w-full text-left px-3 py-2 text-sm rounded-lg transition-colors ${
                      categoryId === cat.categoryId
                        ? 'bg-primary-50 text-primary-700 font-medium'
                        : 'text-gray-700 hover:bg-gray-100'
                    }`}
                  >
                    {cat.name}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </aside>

        {/* 메인 */}
        <div className="flex-1 min-w-0">
          {/* 정렬 */}
          <div className="flex items-center justify-end mb-4 gap-2">
            {(Object.keys(SORT_LABELS) as SortOption[]).map((s) => (
              <button
                key={s}
                onClick={() => updateParam('sort', s)}
                className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
                  sort === s
                    ? 'bg-gray-900 text-white'
                    : 'text-gray-600 hover:bg-gray-100'
                }`}
              >
                {SORT_LABELS[s]}
              </button>
            ))}
          </div>

          {/* 상품 그리드 */}
          {isLoading ? (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 md:gap-6">
              {Array.from({ length: 12 }).map((_, i) => (
                <ProductCardSkeleton key={i} />
              ))}
            </div>
          ) : data?.content.length === 0 ? (
            <EmptyState
              title="검색 결과가 없습니다"
              description="다른 검색어로 시도해보세요."
            />
          ) : (
            <>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 md:gap-6">
                {data?.content.map((product) => (
                  <ProductCard key={product.productId} product={product} />
                ))}
              </div>

              {/* 페이지네이션 */}
              {data && data.totalPages > 1 && (
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
            </>
          )}
        </div>
      </div>
    </div>
  );
}
