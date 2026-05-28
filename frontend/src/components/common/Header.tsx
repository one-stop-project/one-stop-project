import { Link, useNavigate } from 'react-router-dom';
import { ShoppingBag, ShoppingCart, User, LogOut, Search, Heart } from 'lucide-react';
import { useState } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import { useLogoutMutation } from '@/hooks/queries/useAuthQuery';
import { useCartQuery } from '@/hooks/queries/useCartQuery';

export function Header() {
  const navigate = useNavigate();
  const { isAuthenticated, user, hasRole } = useAuthStore();
  const logoutMutation = useLogoutMutation();
  const { data: cart } = useCartQuery();
  const [searchKeyword, setSearchKeyword] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);

  const cartCount = cart?.totalItems ?? 0;

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchKeyword.trim()) {
      navigate(`/products?keyword=${encodeURIComponent(searchKeyword)}`);
    }
  };

  return (
    <header className="sticky top-0 z-50 bg-white border-b border-gray-200">
      {/* 상단 바 */}
      <div className="border-b border-gray-100">
        <div className="max-w-7xl mx-auto px-4 h-9 flex items-center justify-end gap-4 text-xs text-gray-600">
          {hasRole(['ADMIN', 'SUPER_ADMIN']) && (
            <Link to="/admin" className="hover:text-primary-600">
              관리자
            </Link>
          )}
          {hasRole('SELLER') && (
            <Link to="/seller" className="hover:text-primary-600">
              판매자 센터
            </Link>
          )}
          {!isAuthenticated && (
            <>
              <Link to="/login" className="hover:text-primary-600">
                로그인
              </Link>
              <Link to="/signup" className="hover:text-primary-600">
                회원가입
              </Link>
            </>
          )}
        </div>
      </div>

      {/* 메인 헤더 */}
      <div className="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between gap-6">
        {/* 로고 */}
        <Link to="/" className="flex items-center gap-2 shrink-0">
          <div className="w-9 h-9 bg-primary-600 rounded-lg flex items-center justify-center">
            <ShoppingBag className="w-5 h-5 text-white" />
          </div>
          <span className="text-xl font-bold text-gray-900 hidden sm:inline">One-Stop</span>
        </Link>

        {/* 검색 */}
        <form onSubmit={handleSearch} className="flex-1 max-w-2xl">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
            <input
              type="text"
              className="w-full pl-10 pr-4 py-2.5 bg-gray-100 border border-transparent 
                       rounded-lg focus:bg-white focus:border-primary-500 outline-none transition-all"
              placeholder="찾으시는 상품을 검색해보세요"
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
            />
          </div>
        </form>

        {/* 우측 아이콘 */}
        <div className="flex items-center gap-1">
          {isAuthenticated && (
            <>
              <Link
                to="/cart"
                className="relative p-2 hover:bg-gray-100 rounded-lg transition-colors"
                title="장바구니"
              >
                <ShoppingCart size={22} className="text-gray-700" />
                {cartCount > 0 && (
                  <span
                    className="absolute -top-0.5 -right-0.5 bg-primary-600 text-white 
                               text-[10px] font-bold rounded-full min-w-[18px] h-[18px] 
                               flex items-center justify-center px-1"
                  >
                    {cartCount > 99 ? '99+' : cartCount}
                  </span>
                )}
              </Link>

              <div className="relative">
                <button
                  onClick={() => setMenuOpen(!menuOpen)}
                  className="flex items-center gap-2 p-2 hover:bg-gray-100 rounded-lg"
                >
                  <User size={22} className="text-gray-700" />
                  <span className="text-sm font-medium text-gray-700 hidden md:inline">
                    {user?.name}
                  </span>
                </button>

                {menuOpen && (
                  <>
                    <div
                      className="fixed inset-0 z-10"
                      onClick={() => setMenuOpen(false)}
                    />
                    <div
                      className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg 
                                 border border-gray-200 py-1 z-20 animate-fade-in"
                    >
                      <Link
                        to="/mypage"
                        className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                        onClick={() => setMenuOpen(false)}
                      >
                        마이페이지
                      </Link>
                      <Link
                        to="/orders"
                        className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                        onClick={() => setMenuOpen(false)}
                      >
                        주문 내역
                      </Link>
                      <hr className="my-1 border-gray-100" />
                      <button
                        onClick={() => {
                          setMenuOpen(false);
                          logoutMutation.mutate();
                        }}
                        className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center gap-2"
                      >
                        <LogOut size={14} />
                        로그아웃
                      </button>
                    </div>
                  </>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
