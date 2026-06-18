export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const PATHS = {
  login: __ENV.LOGIN_PATH || '/api/auth/login',
  refresh: __ENV.REFRESH_PATH || '/api/auth/refresh',
  products: __ENV.PRODUCTS_PATH || '/api/products',
  couponIssue: __ENV.COUPON_ISSUE_PATH || '/api/coupons/{couponId}/issue',
  orderCreate: __ENV.ORDER_CREATE_PATH || '/api/orders',
  paymentApprove: __ENV.PAYMENT_APPROVE_PATH || '/api/payments/approve',
  pointRead: __ENV.POINT_READ_PATH || '/api/users/me/points',
  sseSubscribe: __ENV.SSE_PATH || '/api/notifications/subscribe',
};
export const url = (path) => `${BASE_URL}${path}`;
export function replacePath(path, values) {
  return Object.entries(values).reduce((r, [k, v]) => r.replace(`{${k}}`, String(v)), path);
}
