/**
 * 백엔드 enum과 1:1 매칭되는 공통 타입
 */

export type UserRole = 'BUYER' | 'SELLER' | 'ADMIN' | 'SUPER_ADMIN';

export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';

export type ProductStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'REJECTED';

export type OrderStatus =
  | 'PENDING' // 결제 대기
  | 'PAID' // 결제 완료
  | 'CONFIRMED' // 판매자 확인
  | 'SHIPPING' // 배송 중
  | 'DELIVERED' // 배송 완료
  | 'CANCELLED' // 취소
  | 'REFUNDED'; // 환불

export type DeliveryStatus =
  | 'PREPARING' // 배송 준비
  | 'SHIPPED' // 발송
  | 'IN_TRANSIT' // 배송 중
  | 'DELIVERED'; // 배송 완료

export type PaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'KAKAO_PAY' | 'TOSS_PAY';

export type SellerStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'INACTIVE';
