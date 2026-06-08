import { useState, useEffect, FormEvent } from 'react';
import { useLocation, Navigate } from 'react-router-dom';
import { useCreateOrderMutation } from '@/hooks/queries/useOrderQuery';
import { useMyInfoQuery } from '@/hooks/queries/useUserQuery';

// ════════════════════════════════════════════════════════════
//  P1-④ 연동 수정: 백엔드 CreateOrderRequest 필드명에 맞춤
//   - CheckoutItem.productItemId → itemId
//   - form.shippingAddress       → receiverAddress
//   - form.message               → deliveryMessage
// ════════════════════════════════════════════════════════════

interface CheckoutItem {
  itemId: number;            // ★ productItemId → itemId
  quantity: number;
}

export default function CheckoutPage() {
  const location = useLocation();
  const { data: me } = useMyInfoQuery();
  const createOrder = useCreateOrderMutation();

  const items = (location.state as { items?: CheckoutItem[] })?.items;

  const [form, setForm] = useState({
    receiverName: '',
    receiverPhone: '',
    receiverAddress: '',       // ★ shippingAddress → receiverAddress
    deliveryMessage: '',       // ★ message → deliveryMessage
  });

  useEffect(() => {
    if (me) {
      setForm((prev) => ({
        ...prev,
        receiverName: me.name,
        receiverPhone: me.phone,
        receiverAddress: me.address,
      }));
    }
  }, [me]);

  if (!items || items.length === 0) {
    return <Navigate to="/cart" replace />;
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    createOrder.mutate(
      {
        orderType: 'DIRECT',
        items,                                       // [{ itemId, quantity }]
        receiverName: form.receiverName,
        receiverPhone: form.receiverPhone,
        receiverAddress: form.receiverAddress,       // ★
        deliveryMessage: form.deliveryMessage || undefined,  // ★
      },
      {
        onError: () => {
          alert('주문 생성에 실패했습니다. 입력 정보를 확인해주세요.');
        },
      }
    );
  };

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">주문/결제</h1>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* 배송지 정보 */}
        <section className="card p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">배송지 정보</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                받는 분 <span className="text-primary-600">*</span>
              </label>
              <input
                type="text"
                className="input-field"
                value={form.receiverName}
                onChange={(e) => setForm({ ...form, receiverName: e.target.value })}
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                연락처 <span className="text-primary-600">*</span>
              </label>
              <input
                type="tel"
                className="input-field"
                value={form.receiverPhone}
                onChange={(e) => setForm({ ...form, receiverPhone: e.target.value })}
                placeholder="010-1234-5678"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                배송 주소 <span className="text-primary-600">*</span>
              </label>
              <input
                type="text"
                className="input-field"
                value={form.receiverAddress}
                onChange={(e) => setForm({ ...form, receiverAddress: e.target.value })}
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                배송 요청사항 (선택 · 최대 50자)
              </label>
              <textarea
                className="input-field"
                value={form.deliveryMessage}
                onChange={(e) => setForm({ ...form, deliveryMessage: e.target.value })}
                placeholder="문 앞에 두고 가주세요 등"
                rows={2}
                maxLength={50}
              />
            </div>
          </div>
        </section>

        {/* 결제 안내 */}
        <section className="card p-6 bg-primary-50 border-primary-200">
          <h2 className="text-lg font-semibold text-gray-900 mb-2">결제 방법 선택</h2>
          <p className="text-sm text-gray-600 mb-4">
            주문 생성 후 결제 페이지로 이동합니다.
          </p>
        </section>

        <button
          type="submit"
          disabled={createOrder.isPending}
          className="btn-primary w-full py-4 text-lg"
        >
          {createOrder.isPending ? '주문 생성 중...' : '주문하고 결제하기'}
        </button>
      </form>
    </div>
  );
}
