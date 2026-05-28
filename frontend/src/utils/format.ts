/**
 * 가격 포맷 (1000 → "1,000원")
 */
export function formatPrice(price: number): string {
  return `${price.toLocaleString('ko-KR')}원`;
}

/**
 * 날짜 포맷 (ISO → "2026.05.27")
 */
export function formatDate(isoString: string | null | undefined): string {
  if (!isoString) return '-';
  const date = new Date(isoString);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
}

/**
 * 날짜+시간 포맷 (ISO → "2026.05.27 15:30")
 */
export function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) return '-';
  const date = new Date(isoString);
  return (
    formatDate(isoString) +
    ` ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  );
}

/**
 * 전화번호 포맷
 */
export function formatPhone(phone: string): string {
  if (!phone) return '';
  return phone.replace(/(\d{3})(\d{4})(\d{4})/, '$1-$2-$3');
}

/**
 * 사업자 번호 포맷
 */
export function formatBusinessNumber(num: string): string {
  if (!num) return '';
  return num.replace(/(\d{3})(\d{2})(\d{5})/, '$1-$2-$3');
}
