import { useState } from 'react';
import { Ticket, Clock, CheckCircle } from 'lucide-react';
import {
  useAvailableCouponsQuery,
  useMyCouponsQuery,
  useIssueCouponMutation,
} from '@/hooks/queries/useCouponQuery';
import {
  AvailableCoupon,
  MyCoupon,
  CouponDiscountType,
} from '@/domains/coupon/couponApi';

type Tab = 'available' | 'my';

function formatDiscount(type: CouponDiscountType, value: number): string {
  return type === 'RATE' ? `${value}% 할인` : `${value.toLocaleString()}원 할인`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

export default function CouponsPage() {
  const [tab, setTab] = useState<Tab>('available');

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex items-center gap-2 mb-6">
        <Ticket className="w-6 h-6 text-primary-600" />
        <h1 className="text-2xl font-bold text-gray-900">쿠폰</h1>
      </div>

      {/* 탭 */}
      <div className="flex border-b border-gray-200 mb-6">
        <button
          onClick={() => setTab('available')}
          className={`px-4 py-2.5 font-medium transition-colors ${
            tab === 'available'
              ? 'text-primary-600 border-b-2 border-primary-600'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          발급 가능
        </button>
        <button
          onClick={() => setTab('my')}
          className={`px-4 py-2.5 font-medium transition-colors ${
            tab === 'my'
              ? 'text-primary-600 border-b-2 border-primary-600'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          내 쿠폰
        </button>
      </div>

      {tab === 'available' ? <AvailableTab /> : <MyTab />}
    </div>
  );
}

// ── 발급 가능 쿠폰 ──
function AvailableTab() {
  const { data: coupons, isLoading } = useAvailableCouponsQuery();
  const issueMutation = useIssueCouponMutation();

  if (isLoading) {
    return <div className="text-center py-12 text-gray-400">불러오는 중...</div>;
  }

  if (!coupons || coupons.length === 0) {
    return (
      <div className="text-center py-12 text-gray-400">
        발급 가능한 쿠폰이 없습니다.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {coupons.map((coupon: AvailableCoupon) => (
        <div
          key={coupon.couponId}
          className="card p-5 flex items-center justify-between"
        >
          <div className="flex-1">
            <p className="text-lg font-bold text-primary-600">
              {formatDiscount(coupon.discountType, coupon.discountValue)}
            </p>
            <p className="font-medium text-gray-900 mt-0.5">{coupon.name}</p>
            <p className="text-sm text-gray-500 mt-1">
              {coupon.minOrderPrice.toLocaleString()}원 이상 구매 시
              {coupon.discountType === 'RATE' &&
                ` · 최대 ${coupon.maxDiscountPrice.toLocaleString()}원`}
            </p>
            <p className="text-xs text-gray-400 mt-1 flex items-center gap-1">
              <Clock className="w-3 h-3" />
              {formatDate(coupon.expiredAt)}까지 · 잔여 {coupon.remainingQuantity}장
            </p>
          </div>
          <button
            onClick={() => issueMutation.mutate(coupon.couponId)}
            disabled={issueMutation.isPending || coupon.remainingQuantity <= 0}
            className="btn-primary px-5 py-2 ml-4 shrink-0"
          >
            {coupon.remainingQuantity <= 0 ? '소진' : '받기'}
          </button>
        </div>
      ))}
    </div>
  );
}

// ── 내 쿠폰 ──
function MyTab() {
  const { data, isLoading } = useMyCouponsQuery();

  if (isLoading) {
    return <div className="text-center py-12 text-gray-400">불러오는 중...</div>;
  }

  const coupons = data?.content ?? [];

  if (coupons.length === 0) {
    return (
      <div className="text-center py-12 text-gray-400">
        보유한 쿠폰이 없습니다.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {coupons.map((coupon: MyCoupon) => {
        const isUsable = coupon.status === 'AVAILABLE';
        return (
          <div
            key={coupon.userCouponId}
            className={`card p-5 ${isUsable ? '' : 'opacity-50'}`}
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-lg font-bold text-primary-600">
                  {formatDiscount(coupon.discountType, coupon.discountValue)}
                </p>
                <p className="font-medium text-gray-900 mt-0.5">
                  {coupon.couponName}
                </p>
                <p className="text-sm text-gray-500 mt-1">
                  {coupon.minOrderPrice.toLocaleString()}원 이상 구매 시
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  {formatDate(coupon.expiredAt)}까지
                </p>
              </div>
              <span
                className={`text-sm font-medium flex items-center gap-1 ${
                  coupon.status === 'AVAILABLE'
                    ? 'text-green-600'
                    : coupon.status === 'USED'
                    ? 'text-gray-400'
                    : 'text-red-400'
                }`}
              >
                {coupon.status === 'AVAILABLE' && <CheckCircle className="w-4 h-4" />}
                {coupon.status === 'AVAILABLE'
                  ? '사용 가능'
                  : coupon.status === 'USED'
                  ? '사용 완료'
                  : '만료'}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
