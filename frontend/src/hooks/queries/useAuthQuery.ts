import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { authApi, LoginRequest, SignUpRequest } from '@/domains/auth/authApi';
import { useAuthStore } from '@/store/useAuthStore';

export function useSignupMutation() {
  const navigate = useNavigate();

  return useMutation({
    mutationFn: (data: SignUpRequest) => authApi.signup(data),
    onSuccess: () => {
      toast.success('회원가입이 완료되었습니다. 로그인해주세요.');
      navigate('/login');
    },
  });
}

export function useLoginMutation() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);

  return useMutation({
    mutationFn: (data: LoginRequest) => authApi.login(data),
    onSuccess: (data) => {
      login(
        {
          userId: data.userId,
          email: data.email,
          name: data.name,
          role: data.role,
        },
        data.accessToken,
        data.expiresIn
      );
      toast.success(`${data.name}님, 환영합니다!`);

      // 역할별 분기
      if (data.role === 'SELLER') navigate('/seller');
      else if (data.role === 'ADMIN' || data.role === 'SUPER_ADMIN') navigate('/admin');
      else navigate('/');
    },
  });
}

export function useLogoutMutation() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logout = useAuthStore((s) => s.logout);

  return useMutation({
    mutationFn: () => authApi.logout(),
    onSuccess: () => {
      logout();
      queryClient.clear();
      toast.success('로그아웃되었습니다.');
      navigate('/login');
    },
    onError: () => {
      // 로그아웃 API 실패해도 로컬 상태는 정리
      logout();
      queryClient.clear();
      navigate('/login');
    },
  });
}
