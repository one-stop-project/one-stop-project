import { Users, Store, Package, ShoppingBag, AlertCircle, TrendingUp } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useDashboardQuery } from '@/hooks/queries/useAdminQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { formatPrice } from '@/utils/format';

export default function AdminDashboard() {
  const { data, isLoading } = useDashboardQuery();

  if (isLoading || !data) return <PageSpinner />;

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">관리자 대시보드</h1>

      {/* 통계 카드 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <StatCard
          icon={<Users />}
          label="전체 회원"
          value={data.totalUsers.toLocaleString()}
          color="bg-blue-50 text-blue-600"
        />
        <StatCard
          icon={<Store />}
          label="전체 판매자"
          value={data.totalSellers.toLocaleString()}
          color="bg-green-50 text-green-600"
        />
        <StatCard
          icon={<Package />}
          label="전체 상품"
          value={data.totalProducts.toLocaleString()}
          color="bg-purple-50 text-purple-600"
        />
        <StatCard
          icon={<ShoppingBag />}
          label="오늘 주문"
          value={data.todayOrders.toLocaleString()}
          color="bg-orange-50 text-orange-600"
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* 처리 대기 */}
        <section className="card p-6">
          <div className="flex items-center gap-2 mb-4">
            <AlertCircle className="text-yellow-600" />
            <h2 className="text-lg font-semibold">처리 대기</h2>
          </div>
          <div className="space-y-3">
            <Link
              to="/admin/products?status=PENDING"
              className="flex items-center justify-between p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
            >
              <span className="text-sm">승인 대기 상품</span>
              <span className="font-bold text-primary-600">{data.pendingProducts}</span>
            </Link>
            <Link
              to="/admin/sellers?status=PENDING"
              className="flex items-center justify-between p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
            >
              <span className="text-sm">승인 대기 판매자</span>
              <span className="font-bold text-primary-600">{data.pendingSellers}</span>
            </Link>
          </div>
        </section>

        {/* 매출 요약 */}
        <section className="card p-6">
          <div className="flex items-center gap-2 mb-4">
            <TrendingUp className="text-green-600" />
            <h2 className="text-lg font-semibold">오늘 매출</h2>
          </div>
          <p className="text-3xl font-bold text-gray-900">{formatPrice(data.todayRevenue)}</p>
          <p className="text-sm text-gray-500 mt-2">총 {data.todayOrders}건의 주문</p>
        </section>
      </div>
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  color: string;
}) {
  return (
    <div className="card p-5">
      <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${color} mb-3`}>
        {icon}
      </div>
      <p className="text-sm text-gray-600 mb-1">{label}</p>
      <p className="text-2xl font-bold text-gray-900">{value}</p>
    </div>
  );
}
