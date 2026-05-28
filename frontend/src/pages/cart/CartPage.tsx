import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShoppingCart, Minus, Plus, X, ChevronRight } from 'lucide-react';
import {
  useCartQuery,
  useUpdateCartItemMutation,
  useRemoveCartItemMutation,
} from '@/hooks/queries/useCartQuery';
import { PageSpinner } from '@/components/common/Spinner';
import { EmptyState } from '@/components/common/EmptyState';
import { formatPrice } from '@/utils/format';

const SHIPPING_FEE = 3000;
const FREE_SHIPPING_THRESHOLD = 50000;

export default function CartPage() {
  const navigate = useNavigate();
  const { data: cart, isLoading } = useCartQuery();
  const updateMutation = useUpdateCartItemMutation();
  const removeMutation = useRemoveCartItemMutation();
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  if (isLoading) return <PageSpinner />;

  // ★ 백엔드는 content 배열, 기준값은 itemId
  const items = cart?.content ?? [];
  const allSelected = items.length > 0 && selectedIds.size === items.length;

  const toggleAll = () => {
    if (allSelected) setSelectedIds(new Set());
    else setSelectedIds(new Set(items.map((i) => i.itemId)));
  };

  const toggleOne = (id: number) => {
    const next = new Set(selectedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelectedIds(next);
  };

  const selectedItems = items.filter((i) => selectedIds.has(i.itemId));
  // subtotal은 price × quantity로 계산 (백엔드에 subtotal 필드 없음)
  const subtotal = selectedItems.reduce((sum, i) => sum + i.price * i.quantity, 0);
  const shippingFee = subtotal >= FREE_SHIPPING_THRESHOLD || subtotal === 0 ? 0 : SHIPPING_FEE;
  const total = subtotal + shippingFee;

  const handleCheckout = () => {
    if (selectedItems.length === 0) return;
    navigate('/checkout', {
      state: {
        items: selectedItems.map((i) => ({
          productItemId: i.itemId,    // 주문 시 itemId를 productItemId로 전달
          quantity: i.quantity,
        })),
      },
    });
  };

  if (items.length === 0) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16">
        <EmptyState
          icon={<ShoppingCart size={40} />}
          title="장바구니가 비어있어요"
          description="마음에 드는 상품을 담아보세요!"
          action={
            <Link to="/products" className="btn-primary inline-block">
              쇼핑 시작하기
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">장바구니</h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 상품 목록 */}
        <div className="lg:col-span-2 space-y-3">
          <div className="card p-4 flex items-center justify-between">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={allSelected}
                onChange={toggleAll}
                className="w-5 h-5 rounded text-primary-600 focus:ring-primary-500"
              />
              <span className="text-sm font-medium">
                전체 선택 ({selectedIds.size}/{items.length})
              </span>
            </label>
          </div>

          {items.map((item) => {
            const itemSubtotal = item.price * item.quantity;
            return (
              <div key={item.itemId} className="card p-4">
                <div className="flex gap-4">
                  <input
                    type="checkbox"
                    checked={selectedIds.has(item.itemId)}
                    onChange={() => toggleOne(item.itemId)}
                    className="w-5 h-5 rounded text-primary-600 focus:ring-primary-500 self-start mt-1"
                  />

                  <Link to={`/products/${item.productId}`} className="shrink-0">
                    <div className="w-20 h-20 md:w-24 md:h-24 bg-gray-100 rounded-lg overflow-hidden">
                      {item.thumbnailUrl ? (
                        <img
                          src={item.thumbnailUrl}
                          alt={item.productName}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center text-xs text-gray-400">
                          이미지
                        </div>
                      )}
                    </div>
                  </Link>

                  <div className="flex-1 min-w-0">
                    <Link
                      to={`/products/${item.productId}`}
                      className="block text-sm font-medium text-gray-900 hover:text-primary-600 truncate"
                    >
                      {item.productName}
                    </Link>
                    <p className="text-xs text-gray-500 mt-0.5">{item.optionName}</p>

                    {!item.available && (
                      <p className="text-xs text-primary-600 mt-1 font-medium">
                        품절 또는 판매 중지된 상품입니다
                      </p>
                    )}

                    <div className="flex items-center justify-between mt-3">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() =>
                            updateMutation.mutate({
                              itemId: item.itemId,
                              quantity: Math.max(1, item.quantity - 1),
                            })
                          }
                          disabled={item.quantity <= 1}
                          className="w-7 h-7 border border-gray-300 rounded flex items-center justify-center hover:bg-gray-50 disabled:opacity-50"
                        >
                          <Minus size={12} />
                        </button>
                        <span className="text-sm font-semibold w-8 text-center">
                          {item.quantity}
                        </span>
                        <button
                          onClick={() =>
                            updateMutation.mutate({
                              itemId: item.itemId,
                              quantity: Math.min(item.stock, item.quantity + 1),
                            })
                          }
                          disabled={item.quantity >= item.stock}
                          className="w-7 h-7 border border-gray-300 rounded flex items-center justify-center hover:bg-gray-50 disabled:opacity-50"
                        >
                          <Plus size={12} />
                        </button>
                      </div>
                      <p className="font-bold text-gray-900">{formatPrice(itemSubtotal)}</p>
                    </div>
                  </div>

                  <button
                    onClick={() => removeMutation.mutate(item.itemId)}
                    className="text-gray-400 hover:text-gray-600 self-start"
                    title="삭제"
                  >
                    <X size={18} />
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* 결제 요약 */}
        <aside className="lg:col-span-1">
          <div className="card p-6 sticky top-32">
            <h2 className="font-bold text-gray-900 mb-4">주문 요약</h2>

            <div className="space-y-3 mb-4 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-600">상품 금액</span>
                <span className="font-medium">{formatPrice(subtotal)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">배송비</span>
                <span className="font-medium">
                  {shippingFee === 0 ? '무료' : formatPrice(shippingFee)}
                </span>
              </div>
              {subtotal > 0 && subtotal < FREE_SHIPPING_THRESHOLD && (
                <p className="text-xs text-primary-600 bg-primary-50 px-3 py-2 rounded">
                  💡 {formatPrice(FREE_SHIPPING_THRESHOLD - subtotal)} 더 담으면 무료배송!
                </p>
              )}
            </div>

            <hr className="my-4" />

            <div className="flex justify-between mb-6">
              <span className="font-semibold">총 결제 금액</span>
              <span className="text-xl font-bold text-primary-600">{formatPrice(total)}</span>
            </div>

            <button
              onClick={handleCheckout}
              disabled={selectedItems.length === 0}
              className="btn-primary w-full py-3 flex items-center justify-center gap-1"
            >
              {selectedItems.length}개 주문하기
              <ChevronRight size={18} />
            </button>
          </div>
        </aside>
      </div>
    </div>
  );
}
