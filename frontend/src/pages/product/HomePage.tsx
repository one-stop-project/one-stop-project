import {ArrowRight, Sparkles} from 'lucide-react';
import {Link} from 'react-router-dom';
import {usePopularProductsQuery, useProductListQuery} from '@/hooks/queries/useProductQuery';
import {ProductCard, ProductCardSkeleton} from '@/components/product/ProductCard';

export default function HomePage() {
  const { data: popular, isLoading: popularLoading } = usePopularProductsQuery(8);
  const { data: latest, isLoading: latestLoading } = useProductListQuery({
    page: 0,
    size: 8,
    sort: 'LATEST',
  });

  // 응답이 깨져도 .map에서 안 터지도록 방어
  const popularProducts = popular?.content ?? [];
  const latestProducts = latest?.content ?? [];

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* 히어로 배너 */}
      <section className="mb-12 relative bg-gradient-to-br from-primary-500 to-primary-700 rounded-2xl overflow-hidden">
        <div className="px-8 md:px-16 py-12 md:py-20 text-white">
          <p className="text-sm font-medium opacity-90 mb-2">진심을 담은 쇼핑</p>
          <h1 className="text-3xl md:text-5xl font-bold mb-4 leading-tight">
            합리적이고 안전한 쇼핑,
            <br />
            One-Stop에서 시작하세요
          </h1>
          <p className="text-base opacity-90 mb-8 max-w-md">
            검증된 판매자와 진정성 있는 상품만을 엄선하여 소개합니다.
          </p>
          <Link
            to="/products"
            className="inline-flex items-center gap-2 bg-white text-primary-700 px-6 py-3 rounded-lg font-semibold hover:bg-gray-50 transition-colors"
          >
            전체 상품 보기
            <ArrowRight size={18} />
          </Link>
        </div>
      </section>

      {/* 인기 상품 */}
      <section className="mb-12">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-2">
            <Sparkles className="text-primary-600" size={24} />
            <h2 className="text-2xl font-bold text-gray-900">인기 상품</h2>
          </div>
          {/* ★ salesCount,desc → POPULAR (백엔드 SortType) */}
          <Link
            to="/products?sort=POPULAR"
            className="text-sm text-gray-600 hover:text-primary-600 flex items-center gap-1"
          >
            더보기
            <ArrowRight size={14} />
          </Link>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 md:gap-6">
          {popularLoading
            ? Array.from({ length: 8 }).map((_, i) => <ProductCardSkeleton key={i} />)
            : popularProducts.map((product) => (
                <ProductCard key={product.productId} product={product} />
              ))}
        </div>
      </section>

      {/* 최신 상품 */}
      <section>
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-gray-900">새로 들어온 상품</h2>
          <Link
            to="/products?sort=LATEST"
            className="text-sm text-gray-600 hover:text-primary-600 flex items-center gap-1"
          >
            더보기
            <ArrowRight size={14} />
          </Link>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 md:gap-6">
          {latestLoading
            ? Array.from({ length: 8 }).map((_, i) => <ProductCardSkeleton key={i} />)
            : latestProducts.map((product) => (
                <ProductCard key={product.productId} product={product} />
              ))}
        </div>
      </section>
    </div>
  );
}
