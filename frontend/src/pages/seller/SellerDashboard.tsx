import { Link } from 'react-router-dom';
import { Package, ShoppingBag, Truck, BarChart3 } from 'lucide-react';
import { useSellerProductsQuery, useSellerOrdersQuery } from '@/hooks/queries/useSellerQuery';

export default function SellerDashboard() {
  const { data: products } = useSellerProductsQuery(0, 5);
  const { data: orders } = useSellerOrdersQuery(0, 5);

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">판매자 대시보드</h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <StatCard
          icon={<Package className="text-blue-600" />}
          label="등록 상품"
          value={products?.totalElements ?? 0}
          link="/seller/products"
        />
        <StatCard
          icon={<ShoppingBag className="text-green-600" />}
          label="전체 주문"
          value={orders?.totalElements ?? 0}
          link="/seller/orders"
        />
        <StatCard
          icon={<Truck className="text-orange-600" />}
          label="배송 중"
          value={0}
          link="/seller/orders"
        />
        <StatCard
          icon={<BarChart3 className="text-purple-600" />}
          label="이번 달 매출"
          value="-"
          link="#"
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <section className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">최근 등록 상품</h2>
            <Link to="/seller/products" className="text-sm text-primary-600 hover:underline">
              전체 보기
            </Link>
          </div>
          <div className="space-y-3">
            {products?.content.slice(0, 5).map((p) => (
              <div key={p.productId} className="flex items-center gap-3 text-sm">
                <div className="w-10 h-10 bg-gray-100 rounded shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="font-medium truncate">{p.name}</p>
                  <p className="text-xs text-gray-500">{p.categoryName}</p>
                </div>
                <span className="text-xs px-2 py-1 bg-gray-100 rounded">{p.status}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">최근 주문</h2>
            <Link to="/seller/orders" className="text-sm text-primary-600 hover:underline">
              전체 보기
            </Link>
          </div>
          <div className="space-y-3">
            {orders?.content.slice(0, 5).map((o) => (
              <div key={o.orderItemId} className="text-sm border-b pb-2 last:border-0">
                <p className="font-medium">{o.productName}</p>
                <p className="text-xs text-gray-500">
                  {o.receiverName} · {o.quantity}개
                </p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  link,
}: {
  icon: React.ReactNode;
  label: string;
  value: number | string;
  link: string;
}) {
  return (
    <Link to={link} className="card p-5 hover:shadow-md transition-shadow">
      <div className="flex items-center gap-3 mb-2">
        <div className="w-10 h-10 bg-gray-50 rounded-lg flex items-center justify-center">
          {icon}
        </div>
        <span className="text-sm text-gray-600">{label}</span>
      </div>
      <p className="text-2xl font-bold text-gray-900">{value}</p>
    </Link>
  );
}
