import { useEffect, useState, ReactNode } from 'react';
import { authApi } from '@/domains/auth/authApi';
import { userApi } from '@/domains/user/userApi';
import { useAuthStore } from '@/store/useAuthStore';
import { setAccessToken } from '@/api/client';
import { PageSpinner } from '@/components/common/Spinner';

/**
 * 앱 시작 시 세션 복구
 *
 * AT는 메모리에만 저장 → 새로고침 시 사라짐
 * HttpOnly RT 쿠키로 refresh 시도 → 성공하면 세션 복구
 */
export function AuthInitializer({ children }: { children: ReactNode }) {
  const [initializing, setInitializing] = useState(true);
  const login = useAuthStore((s) => s.login);

  useEffect(() => {
    const restoreSession = async () => {
      try {
        // RT 쿠키로 AT 재발급 시도
        const { accessToken, expiresIn } = await authApi.refresh();
        setAccessToken(accessToken);

        // 사용자 정보 조회
        const me = await userApi.getMe();
        login(
          {
            userId: me.userId,
            email: me.email,
            name: me.name,
            role: me.role,
          },
          accessToken,
          expiresIn
        );
      } catch {
        // RT 없거나 만료 → 비로그인 상태로 진행 (정상)
      } finally {
        setInitializing(false);
      }
    };

    restoreSession();
  }, [login]);

  if (initializing) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <PageSpinner />
      </div>
    );
  }

  return <>{children}</>;
}
