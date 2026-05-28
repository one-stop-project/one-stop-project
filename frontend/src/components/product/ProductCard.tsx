import { Link } from 'react-router-dom';
import { TrendingUp } from 'lucide-react';
import { ProductSummary } from '@/domains/product/productApi';
import { formatPrice } from '@/utils/format';

interface ProductCardProps {
  product: ProductSummary;
}

export function ProductCard({ product }: ProductCardProps) {
  return (
    <Link to={`/products/${product.productId}`} className="group block">
      <div className="relative aspect-square bg-gray-100 rounded-xl overflow-hidden mb-3">
        {product.thumbnailUrl ? (
          <img
            src={product.thumbnailUrl}
            alt={product.name}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            loading="lazy"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-400 text-sm">
            이미지 없음
          </div>
        )}
      </div>

      <div className="space-y-1">
        <h3 className="text-sm font-medium text-gray-900 line-clamp-2 group-hover:text-primary-600 transition-colors">
          {product.name}
        </h3>
        <p className="text-lg font-bold text-gray-900">{formatPrice(product.minPrice)}</p>

        {product.salesCount > 0 && (
          <div className="flex items-center gap-1 text-xs text-gray-500">
            <TrendingUp size={12} className="text-primary-500" />
            <span>{product.salesCount.toLocaleString()}개 판매</span>
          </div>
        )}
      </div>
    </Link>
  );
}

export function ProductCardSkeleton() {
  return (
    <div className="animate-pulse">
      <div className="aspect-square bg-gray-200 rounded-xl mb-3" />
      <div className="space-y-2">
        <div className="h-4 bg-gray-200 rounded" />
        <div className="h-5 bg-gray-200 rounded w-1/2" />
      </div>
    </div>
  );
}
