import { Outlet, Link, useLocation } from 'react-router-dom';
import { ShoppingBag, LucideIcon } from 'lucide-react';
import { Header } from '@/components/common/Header';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
}

interface DashboardLayoutProps {
  title: string;
  navItems: NavItem[];
}

export function DashboardLayout({ title, navItems }: DashboardLayoutProps) {
  const location = useLocation();

  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <div className="flex-1 flex">
        {/* 사이드바 */}
        <aside className="w-60 bg-white border-r border-gray-200 shrink-0 hidden md:block">
          <div className="p-6 border-b border-gray-100">
            <div className="flex items-center gap-2">
              <ShoppingBag className="text-primary-600" size={20} />
              <span className="font-bold text-gray-900">{title}</span>
            </div>
          </div>
          <nav className="p-3 space-y-1">
            {navItems.map((item) => {
              const isActive = location.pathname === item.to;
              const Icon = item.icon;
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                    isActive
                      ? 'bg-primary-50 text-primary-700 font-medium'
                      : 'text-gray-700 hover:bg-gray-50'
                  }`}
                >
                  <Icon size={18} />
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </aside>

        {/* 메인 */}
        <main className="flex-1 bg-gray-50 min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
