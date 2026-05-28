import { Outlet } from 'react-router-dom';
import { Header } from '@/components/common/Header';

export function MainLayout() {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}

function Footer() {
  return (
    <footer className="bg-gray-900 text-gray-400 mt-20">
      <div className="max-w-7xl mx-auto px-4 py-12">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
          <div>
            <h3 className="text-white font-semibold mb-3">One-Stop</h3>
            <p className="text-xs leading-relaxed">
              진심을 담은 쇼핑
              <br />
              합리적이고 안전한 거래
            </p>
          </div>
          <div>
            <h4 className="text-sm font-semibold text-gray-200 mb-3">고객센터</h4>
            <ul className="space-y-2 text-xs">
              <li>1588-0000</li>
              <li>평일 09:00 - 18:00</li>
            </ul>
          </div>
          <div>
            <h4 className="text-sm font-semibold text-gray-200 mb-3">서비스</h4>
            <ul className="space-y-2 text-xs">
              <li><a href="#" className="hover:text-white">자주 묻는 질문</a></li>
              <li><a href="#" className="hover:text-white">배송 안내</a></li>
              <li><a href="#" className="hover:text-white">교환/반품</a></li>
            </ul>
          </div>
          <div>
            <h4 className="text-sm font-semibold text-gray-200 mb-3">회사</h4>
            <ul className="space-y-2 text-xs">
              <li><a href="#" className="hover:text-white">회사 소개</a></li>
              <li><a href="#" className="hover:text-white">판매자 등록</a></li>
              <li><a href="#" className="hover:text-white">이용약관</a></li>
            </ul>
          </div>
        </div>
        <div className="mt-10 pt-6 border-t border-gray-800 text-xs text-center">
          © 2026 One-Stop. All rights reserved.
        </div>
      </div>
    </footer>
  );
}
