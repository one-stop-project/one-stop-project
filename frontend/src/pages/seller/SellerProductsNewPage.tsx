import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Trash2 } from 'lucide-react';
import { useCreateProductMutation } from '@/hooks/queries/useSellerQuery';
import { useCategoriesQuery } from '@/hooks/queries/useProductQuery';

interface ItemForm {
  name: string;
  price: string;
  stock: string;
}

export default function SellerProductsNewPage() {
  const navigate = useNavigate();
  const { data: categories } = useCategoriesQuery();
  const createMutation = useCreateProductMutation();

  const [form, setForm] = useState({
    name: '',
    description: '',
    price: '',
    categoryId: '',
  });
  const [items, setItems] = useState<ItemForm[]>([{ name: '', price: '', stock: '' }]);

  const addItem = () => setItems([...items, { name: '', price: '', stock: '' }]);
  const removeItem = (idx: number) => setItems(items.filter((_, i) => i !== idx));
  const updateItem = (idx: number, field: keyof ItemForm, value: string) =>
    setItems(items.map((item, i) => (i === idx ? { ...item, [field]: value } : item)));

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    createMutation.mutate(
      {
        name: form.name,
        description: form.description,
        price: Number(form.price),
        categoryId: Number(form.categoryId),
        items: items.map((i) => ({
          name: i.name,
          price: Number(i.price),
          stock: Number(i.stock),
        })),
      },
      {
        onSuccess: () => navigate('/seller/products'),
      }
    );
  };

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">상품 등록</h1>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* 기본 정보 */}
        <section className="card p-6 space-y-4">
          <h2 className="text-lg font-semibold">기본 정보</h2>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              상품명 <span className="text-primary-600">*</span>
            </label>
            <input
              className="input-field"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              카테고리 <span className="text-primary-600">*</span>
            </label>
            <select
              className="input-field"
              value={form.categoryId}
              onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
              required
            >
              <option value="">선택해주세요</option>
              {categories?.map((c) => (
                <option key={c.categoryId} value={c.categoryId}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              대표 가격 <span className="text-primary-600">*</span>
            </label>
            <input
              type="number"
              className="input-field"
              value={form.price}
              onChange={(e) => setForm({ ...form, price: e.target.value })}
              placeholder="0"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              상품 설명 <span className="text-primary-600">*</span>
            </label>
            <textarea
              className="input-field"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows={5}
              required
            />
          </div>
        </section>

        {/* 옵션 (상품 아이템) */}
        <section className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">옵션 및 재고</h2>
            <button
              type="button"
              onClick={addItem}
              className="text-sm text-primary-600 flex items-center gap-1 hover:text-primary-700"
            >
              <Plus size={16} />
              옵션 추가
            </button>
          </div>

          <div className="space-y-3">
            {items.map((item, idx) => (
              <div key={idx} className="flex gap-2 items-end">
                <div className="flex-1">
                  <label className="block text-xs text-gray-500 mb-1">옵션명</label>
                  <input
                    className="input-field"
                    value={item.name}
                    onChange={(e) => updateItem(idx, 'name', e.target.value)}
                    placeholder="예: 빨강 / M"
                    required
                  />
                </div>
                <div className="w-28">
                  <label className="block text-xs text-gray-500 mb-1">가격</label>
                  <input
                    type="number"
                    className="input-field"
                    value={item.price}
                    onChange={(e) => updateItem(idx, 'price', e.target.value)}
                    required
                  />
                </div>
                <div className="w-24">
                  <label className="block text-xs text-gray-500 mb-1">재고</label>
                  <input
                    type="number"
                    className="input-field"
                    value={item.stock}
                    onChange={(e) => updateItem(idx, 'stock', e.target.value)}
                    required
                  />
                </div>
                {items.length > 1 && (
                  <button
                    type="button"
                    onClick={() => removeItem(idx)}
                    className="p-2.5 text-red-500 hover:bg-red-50 rounded-lg"
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
            ))}
          </div>
        </section>

        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => navigate('/seller/products')}
            className="btn-secondary flex-1"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="btn-primary flex-1"
          >
            {createMutation.isPending ? '등록 중...' : '상품 등록'}
          </button>
        </div>
      </form>
    </div>
  );
}
