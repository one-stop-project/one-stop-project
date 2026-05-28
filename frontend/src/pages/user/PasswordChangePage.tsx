import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useChangePasswordMutation } from '@/hooks/queries/useUserQuery';
import { useLogoutMutation } from '@/hooks/queries/useAuthQuery';

export default function PasswordChangePage() {
  const navigate = useNavigate();
  const changeMutation = useChangePasswordMutation();
  const logoutMutation = useLogoutMutation();

  const [form, setForm] = useState({
    currentPassword: '',
    newPassword: '',
    newPasswordConfirm: '',
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();

    if (form.newPassword !== form.newPasswordConfirm) {
      alert('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    changeMutation.mutate(
      {
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      },
      {
        onSuccess: () => {
          // 비밀번호 변경 시 전체 세션 무효화 → 재로그인
          logoutMutation.mutate();
        },
      }
    );
  };

  return (
    <div className="max-w-md mx-auto px-4 py-12">
      <div className="card p-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">비밀번호 변경</h1>
        <p className="text-sm text-gray-600 mb-6">
          변경 후에는 보안을 위해 다시 로그인해야 합니다.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              현재 비밀번호
            </label>
            <input
              type="password"
              className="input-field"
              value={form.currentPassword}
              onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              새 비밀번호
            </label>
            <input
              type="password"
              className="input-field"
              value={form.newPassword}
              onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
              placeholder="8자 이상"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              새 비밀번호 확인
            </label>
            <input
              type="password"
              className="input-field"
              value={form.newPasswordConfirm}
              onChange={(e) => setForm({ ...form, newPasswordConfirm: e.target.value })}
              required
            />
          </div>

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={() => navigate('/mypage')} className="btn-secondary flex-1">
              취소
            </button>
            <button type="submit" disabled={changeMutation.isPending} className="btn-primary flex-1">
              변경하기
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
