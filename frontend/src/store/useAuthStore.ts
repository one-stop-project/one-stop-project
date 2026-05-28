import { create } from 'zustand';
import { UserRole } from '@/types/common';
import { clearAccessToken, setAccessToken } from '@/api/client';

interface User {
  userId: number;
  email: string;
  name: string;
  role: UserRole;
}

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  expiresAt: number | null; // Unix timestamp (ms)

  // Actions
  login: (user: User, accessToken: string, expiresIn: number) => void;
  logout: () => void;
  updateUser: (partial: Partial<User>) => void;
  hasRole: (role: UserRole | UserRole[]) => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  expiresAt: null,

  login: (user, accessToken, expiresIn) => {
    setAccessToken(accessToken);
    set({
      user,
      isAuthenticated: true,
      expiresAt: Date.now() + expiresIn * 1000,
    });
  },

  logout: () => {
    clearAccessToken();
    set({
      user: null,
      isAuthenticated: false,
      expiresAt: null,
    });
  },

  updateUser: (partial) => {
    const current = get().user;
    if (!current) return;
    set({ user: { ...current, ...partial } });
  },

  hasRole: (role) => {
    const userRole = get().user?.role;
    if (!userRole) return false;
    if (Array.isArray(role)) return role.includes(userRole);
    return userRole === role;
  },
}));
