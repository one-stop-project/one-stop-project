import {BrowserRouter, Route, Routes} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {ReactQueryDevtools} from '@tanstack/react-query-devtools';
import {Toaster} from 'react-hot-toast';

import {MainLayout} from '@/components/layout/MainLayout';
import {SellerLayout} from '@/components/layout/SellerLayout';
import {AdminLayout} from '@/components/layout/AdminLayout';
import {ProtectedRoute} from '@/components/route/ProtectedRoute';
import {AuthInitializer} from '@/components/route/AuthInitializer';

// 👈 추가된 부분 1: 컴포넌트 임포트
import CouponsPage from '@/pages/coupon/CouponsPage';
import AiAssistantWidget from '@/components/AiAssistantWidget';

// Auth
import LoginPage from '@/pages/auth/LoginPage';
import SignupPage from '@/pages/auth/SignupPage';

// Product
import HomePage from '@/pages/product/HomePage';
import ProductListPage from '@/pages/product/ProductListPage';
import ProductDetailPage from '@/pages/product/ProductDetailPage';

// Cart / Order / Payment
import CartPage from '@/pages/cart/CartPage';
import CheckoutPage from '@/pages/order/CheckoutPage';
import OrderListPage from '@/pages/order/OrderListPage';
import OrderDetailPage from '@/pages/order/OrderDetailPage';
import OrderCompletePage from '@/pages/order/OrderCompletePage';
import PaymentPage from '@/pages/payment/PaymentPage';

// User
import MyPage from '@/pages/user/MyPage';
import PasswordChangePage from '@/pages/user/PasswordChangePage';
import WithdrawPage from '@/pages/user/WithdrawPage';

// Seller
import SellerDashboard from '@/pages/seller/SellerDashboard';
import SellerProductsPage from '@/pages/seller/SellerProductsPage';
import SellerProductsNewPage from '@/pages/seller/SellerProductsNewPage';
import SellerInventoryPage from '@/pages/seller/SellerInventoryPage';
import SellerOrdersPage from '@/pages/seller/SellerOrdersPage';

// Admin
import AdminDashboard from '@/pages/admin/AdminDashboard';
import AdminProductsPage from '@/pages/admin/AdminProductsPage';
import AdminSellersPage from '@/pages/admin/AdminSellersPage';
import AdminOrdersPage from '@/pages/admin/AdminOrdersPage';

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: 1,
            refetchOnWindowFocus: false,
            staleTime: 30 * 1000,
        },
    },
});

export default function App() {
    return (
        <QueryClientProvider client={queryClient}>
            <BrowserRouter>
                <AuthInitializer>
                    <Routes>
                        {/* 인증 (레이아웃 없음) */}
                        <Route path="/login" element={<LoginPage />} />
                        <Route path="/signup" element={<SignupPage />} />

                        {/* 메인 레이아웃 (구매자/공통) */}
                        <Route element={<MainLayout />}>
                            <Route path="/" element={<HomePage />} />
                            <Route path="/products" element={<ProductListPage />} />
                            <Route path="/products/:id" element={<ProductDetailPage />} />

                            {/* 인증 필요 */}
                            <Route element={<ProtectedRoute />}>
                                <Route path="/cart" element={<CartPage />} />
                                <Route path="/checkout" element={<CheckoutPage />} />
                                <Route path="/payment/:id" element={<PaymentPage />} />
                                <Route path="/orders" element={<OrderListPage />} />
                                <Route path="/orders/:id" element={<OrderDetailPage />} />
                                <Route path="/orders/:id/complete" element={<OrderCompletePage />} />

                                {/* 👈 추가된 부분 2: 로그인한 사용자만 접근 가능한 쿠폰 페이지 */}
                                <Route path="/coupons" element={<CouponsPage />} />

                                <Route path="/mypage" element={<MyPage />} />
                                <Route path="/mypage/password" element={<PasswordChangePage />} />
                                <Route path="/mypage/withdraw" element={<WithdrawPage />} />
                            </Route>
                        </Route>

                        {/* 판매자 (SELLER 권한) */}
                        <Route element={<ProtectedRoute roles="SELLER" />}>
                            <Route element={<SellerLayout />}>
                                <Route path="/seller" element={<SellerDashboard />} />
                                <Route path="/seller/products" element={<SellerProductsPage />} />
                                <Route path="/seller/products/new" element={<SellerProductsNewPage />} />
                                <Route path="/seller/inventory" element={<SellerInventoryPage />} />
                                <Route path="/seller/orders" element={<SellerOrdersPage />} />
                            </Route>
                        </Route>

                        {/* 관리자 (ADMIN/SUPER_ADMIN 권한) */}
                        <Route element={<ProtectedRoute roles={['ADMIN', 'SUPER_ADMIN']} />}>
                            <Route element={<AdminLayout />}>
                                <Route path="/admin" element={<AdminDashboard />} />
                                <Route path="/admin/products" element={<AdminProductsPage />} />
                                <Route path="/admin/sellers" element={<AdminSellersPage />} />
                                <Route path="/admin/orders" element={<AdminOrdersPage />} />
                            </Route>
                        </Route>

                        {/* 404 */}
                        <Route path="*" element={<NotFound />} />
                    </Routes>

                    {/* 👈 추가된 부분 3: AI 어시스턴트 위젯 배치 */}
                    <AiAssistantWidget />

                </AuthInitializer>
            </BrowserRouter>

            <Toaster
                position="top-center"
                toastOptions={{
                    duration: 3000,
                    style: {
                        borderRadius: '10px',
                        fontSize: '14px',
                    },
                }}
            />

            {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
        </QueryClientProvider>
    );
}

function NotFound() {
    return (
        <div className="min-h-screen flex flex-col items-center justify-center gap-4">
            <h1 className="text-6xl font-bold text-gray-300">404</h1>
            <p className="text-gray-600">페이지를 찾을 수 없습니다.</p>
            <a href="/" className="btn-primary">
                홈으로 돌아가기
            </a>
        </div>
    );
}
