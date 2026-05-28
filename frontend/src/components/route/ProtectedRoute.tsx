import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { UserRole } from '@/types/common';

interface ProtectedRouteProps {
  roles?: UserRole | UserRole[];
}

export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const location = useLocation();
  const { isAuthenticated, hasRole } = useAuthStore();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (roles && !hasRole(roles)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
