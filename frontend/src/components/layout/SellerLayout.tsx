import { LayoutDashboard, Package, ShoppingBag, Boxes } from 'lucide-react';
import { DashboardLayout } from './DashboardLayout';

export function SellerLayout() {
  return (
    <DashboardLayout
      title="판매자 센터"
      navItems={[
        { to: '/seller', label: '대시보드', icon: LayoutDashboard },
        { to: '/seller/products', label: '상품 관리', icon: Package },
        { to: '/seller/inventory', label: '재고 관리', icon: Boxes },
        { to: '/seller/orders', label: '주문 관리', icon: ShoppingBag },
      ]}
    />
  );
}
