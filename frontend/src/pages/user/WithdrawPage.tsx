import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { useWithdrawMutation } from '@/hooks/queries/useUserQuery';
import { useMyInfoQuery } from '@/hooks/queries/useUserQuery';

export default function WithdrawPage() {
  const navigate = useNavigate();
  const { data: me } = useMyInfoQuery();
  const withdrawMutation = useWithdrawMutation();

  const [password, setPassword] = useState('');
  const [agreed, setAgreed] = useState(false);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!agreed) return;
    // OAuth2 사용자는 비밀번호 불필요
    withdrawMutation.mutate({ password: me?.isOAuth2User ? '' : password });
  };

  return (
    <div className="max-w-md mx-auto px-4 py-12">
      <div className="card p-8">
        <div className="w-14 h-14 bg-red-50 text-red-600 rounded-full flex items-center justify-center mx-auto mb-4">
          <AlertTriangle size={28} />
        </div>

        <h1 className="text-2xl font-bold text-gray-900 text-center mb-2">회원 탈퇴</h1>
        <p className="text-sm text-gray-600 text-center mb-6">
          정말 떠나시는 건가요? 탈퇴 전 아래 내용을 확인해주세요.
        </p>

        <div className="bg-red-50 border border-red-100 rounded-lg p-4 mb-6 text-sm text-gray-700 space-y-2">
          <p>• 모든 개인정보가 삭제됩니다.</p>
          <p>• 진행 중인 주문이 있다면 완료 후 탈퇴해주세요.</p>
          <p>• 탈퇴 후에는 복구할 수 없습니다.</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {!me?.isOAuth2User && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                비밀번호 확인
              </label>
              <input
                type="password"
                className="input-field"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
          )}

          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={agreed}
              onChange={(e) => setAgreed(e.target.checked)}
              className="w-4 h-4 rounded text-primary-600"
            />
            <span className="text-sm text-gray-700">위 내용을 모두 확인했습니다.</span>
          </label>

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={() => navigate('/mypage')} className="btn-secondary flex-1">
              취소
            </button>
            <button
              type="submit"
              disabled={!agreed || withdrawMutation.isPending}
              className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg font-medium hover:bg-red-700 transition-colors disabled:opacity-50"
            >
              탈퇴하기
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
