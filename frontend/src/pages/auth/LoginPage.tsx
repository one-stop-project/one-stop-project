import { useState, FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Eye, EyeOff, ShoppingBag } from 'lucide-react';
import { useLoginMutation } from '@/hooks/queries/useAuthQuery';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const loginMutation = useLoginMutation();

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!email || !password) return;
    loginMutation.mutate({ email, password });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 via-white to-gray-50 p-4">
      <div className="w-full max-w-md">
        {/* 로고 */}
        <Link to="/" className="flex items-center justify-center gap-2 mb-8">
          <div className="w-12 h-12 bg-primary-600 rounded-xl flex items-center justify-center">
            <ShoppingBag className="w-7 h-7 text-white" />
          </div>
          <span className="text-2xl font-bold text-gray-900">One-Stop</span>
        </Link>

        {/* 카드 */}
        <div className="card p-8 animate-fade-in">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900 mb-2">로그인</h1>
            <p className="text-sm text-gray-600">진심을 담은 쇼핑, One-Stop에 오신 것을 환영합니다.</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                이메일
              </label>
              <input
                type="email"
                className="input-field"
                placeholder="email@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                비밀번호
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="input-field pr-10"
                  placeholder="비밀번호 입력"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loginMutation.isPending}
              className="btn-primary w-full py-3"
            >
              {loginMutation.isPending ? '로그인 중...' : '로그인'}
            </button>
          </form>

          {/* 소셜 로그인 */}
          <div className="mt-6">
            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-gray-200" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="px-2 bg-white text-gray-500">또는</span>
              </div>
            </div>

            <a
              href="/oauth2/authorization/kakao"
              className="mt-4 w-full flex items-center justify-center gap-2 py-3 bg-[#FEE500] text-[#3C1E1E] rounded-lg font-medium hover:bg-[#FFD700] transition-colors"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 3C6.48 3 2 6.48 2 11c0 2.9 1.94 5.45 4.85 6.92L5.5 21l3.92-2.16c.83.14 1.7.22 2.58.22 5.52 0 10-3.48 10-8s-4.48-8-10-8z" />
              </svg>
              카카오로 시작하기
            </a>
          </div>

          {/* 회원가입 */}
          <div className="mt-6 text-center text-sm text-gray-600">
            아직 회원이 아니신가요?{' '}
            <Link to="/signup" className="text-primary-600 hover:text-primary-700 font-medium">
              회원가입
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
