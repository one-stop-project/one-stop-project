import { useState, FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ShoppingBag } from 'lucide-react';
import { useSignupMutation } from '@/hooks/queries/useAuthQuery';

type UserType = 'BUYER' | 'SELLER';

export default function SignupPage() {
  const [userType, setUserType] = useState<UserType>('BUYER');
  const [form, setForm] = useState({
    email: '',
    password: '',
    passwordConfirm: '',
    name: '',
    phone: '',
    address: '',
    shopName: '',
    businessNumber: '',
    bankAccount: '',
  });

  const signupMutation = useSignupMutation();

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();

    if (form.password !== form.passwordConfirm) {
      alert('비밀번호가 일치하지 않습니다.');
      return;
    }

    const { passwordConfirm, ...payload } = form;

    if (userType === 'BUYER') {
      const { shopName, businessNumber, bankAccount, ...buyerPayload } = payload;
      signupMutation.mutate(buyerPayload as any);
    } else {
      signupMutation.mutate(payload);
    }
  };

  const update = (field: keyof typeof form, value: string) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-50 via-white to-gray-50 py-12 px-4">
      <div className="w-full max-w-lg mx-auto">
        <Link to="/" className="flex items-center justify-center gap-2 mb-8">
          <div className="w-12 h-12 bg-primary-600 rounded-xl flex items-center justify-center">
            <ShoppingBag className="w-7 h-7 text-white" />
          </div>
          <span className="text-2xl font-bold text-gray-900">One-Stop</span>
        </Link>

        <div className="card p-8 animate-fade-in">
          <h1 className="text-2xl font-bold text-gray-900 mb-2">회원가입</h1>
          <p className="text-sm text-gray-600 mb-6">진심을 담은 쇼핑을 함께 시작해보세요.</p>

          {/* 회원 유형 선택 */}
          <div className="grid grid-cols-2 gap-3 mb-6">
            {(['BUYER', 'SELLER'] as UserType[]).map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setUserType(type)}
                className={`py-3 rounded-lg font-medium border-2 transition-all ${
                  userType === type
                    ? 'border-primary-600 bg-primary-50 text-primary-700'
                    : 'border-gray-200 bg-white text-gray-600 hover:border-gray-300'
                }`}
              >
                {type === 'BUYER' ? '🛍️ 구매자' : '🏪 판매자'}
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* 공통 필드 */}
            <FormField label="이메일" required>
              <input
                type="email"
                className="input-field"
                value={form.email}
                onChange={(e) => update('email', e.target.value)}
                placeholder="email@example.com"
                required
              />
            </FormField>

            <div className="grid grid-cols-2 gap-3">
              <FormField label="비밀번호" required>
                <input
                  type="password"
                  className="input-field"
                  value={form.password}
                  onChange={(e) => update('password', e.target.value)}
                  placeholder="8자 이상"
                  required
                />
              </FormField>
              <FormField label="비밀번호 확인" required>
                <input
                  type="password"
                  className="input-field"
                  value={form.passwordConfirm}
                  onChange={(e) => update('passwordConfirm', e.target.value)}
                  required
                />
              </FormField>
            </div>

            <FormField label="이름" required>
              <input
                type="text"
                className="input-field"
                value={form.name}
                onChange={(e) => update('name', e.target.value)}
                required
              />
            </FormField>

            <FormField label="전화번호" required>
              <input
                type="tel"
                className="input-field"
                value={form.phone}
                onChange={(e) => update('phone', e.target.value)}
                placeholder="010-1234-5678"
                required
              />
            </FormField>

            <FormField label="주소" required>
              <input
                type="text"
                className="input-field"
                value={form.address}
                onChange={(e) => update('address', e.target.value)}
                placeholder="서울시 강남구..."
                required
              />
            </FormField>

            {/* 판매자 추가 필드 */}
            {userType === 'SELLER' && (
              <div className="space-y-4 p-4 bg-gray-50 rounded-lg border border-gray-200 animate-slide-up">
                <p className="text-sm font-medium text-gray-700">판매자 추가 정보</p>

                <FormField label="상호명" required>
                  <input
                    type="text"
                    className="input-field"
                    value={form.shopName}
                    onChange={(e) => update('shopName', e.target.value)}
                    required
                  />
                </FormField>

                <FormField label="사업자등록번호" required>
                  <input
                    type="text"
                    className="input-field"
                    value={form.businessNumber}
                    onChange={(e) => update('businessNumber', e.target.value)}
                    placeholder="123-45-67890"
                    required
                  />
                </FormField>

                <FormField label="정산 계좌">
                  <input
                    type="text"
                    className="input-field"
                    value={form.bankAccount}
                    onChange={(e) => update('bankAccount', e.target.value)}
                    placeholder="은행명 + 계좌번호"
                  />
                </FormField>
              </div>
            )}

            <button
              type="submit"
              disabled={signupMutation.isPending}
              className="btn-primary w-full py-3 mt-6"
            >
              {signupMutation.isPending ? '가입 중...' : '회원가입'}
            </button>
          </form>

          <div className="mt-6 text-center text-sm text-gray-600">
            이미 회원이신가요?{' '}
            <Link to="/login" className="text-primary-600 hover:text-primary-700 font-medium">
              로그인
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

function FormField({
  label,
  required,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1.5">
        {label}
        {required && <span className="text-primary-600 ml-0.5">*</span>}
      </label>
      {children}
    </div>
  );
}
