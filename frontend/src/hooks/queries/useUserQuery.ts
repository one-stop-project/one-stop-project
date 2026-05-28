import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  userApi,
  UserUpdateRequest,
  PasswordChangeRequest,
  WithdrawRequest,
} from '@/domains/user/userApi';
import { useAuthStore } from '@/store/useAuthStore';
import { useNavigate } from 'react-router-dom';

export function useMyInfoQuery() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ['user', 'me'],
    queryFn: () => userApi.getMe(),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5분
  });
}

export function useUpdateMyInfoMutation() {
  const queryClient = useQueryClient();
  const updateUser = useAuthStore((s) => s.updateUser);

  return useMutation({
    mutationFn: (data: UserUpdateRequest) => userApi.updateMe(data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['user', 'me'] });
      updateUser({ name: data.name });
      toast.success('정보가 수정되었습니다.');
    },
  });
}

export function useChangePasswordMutation() {
  return useMutation({
    mutationFn: (data: PasswordChangeRequest) => userApi.changePassword(data),
    onSuccess: () => {
      toast.success('비밀번호가 변경되었습니다. 다시 로그인해주세요.');
    },
  });
}

export function useWithdrawMutation() {
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  return useMutation({
    mutationFn: (data: WithdrawRequest) => userApi.withdraw(data),
    onSuccess: () => {
      logout();
      toast.success('탈퇴가 완료되었습니다.');
      navigate('/');
    },
  });
}
