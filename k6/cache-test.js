/**
 * 상품 검색 캐시 성능 비교 테스트
 *
 * 실행 전 준비:
 *   1. Redis productList 캐시 삭제:
 *      redis-cli KEYS "cache:productList*" | ForEach-Object { redis-cli DEL $_ }
 *   2. 서버 기동 확인 (localhost:8080)
 *
 * 실행:
 *   k6 run k6/cache-test.js --summary-export=k6/cache-result.json
 *
 * 측정 대상:
 *   - Stage 1 (cold):  캐시 미스 — DB 직접 조회
 *   - Stage 2 (warm):  캐시 히트 — Redis 반환
 *
 * 캐시 정책:
 *   - @Cacheable(value="productList", cacheManager="redisCacheManager", TTL=5분)
 *   - POPULAR 정렬은 캐시 제외 (condition에서 명시적으로 배제)
 *   - 캐시 키: keyword:categoryId:minPrice:maxPrice:sortType:pageNumber:pageSize
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const coldDuration = new Trend('cache_cold_duration', true);  // 캐시 미스
const warmDuration = new Trend('cache_warm_duration', true);  // 캐시 히트

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트할 검색 파라미터 조합 (캐시 키 다양화)
// POPULAR 정렬 제외: @Cacheable condition에서 명시적 제외 + 랭킹 데이터 없으면 응답 불안정
const SEARCH_PARAMS = [
  '',                                       // 전체 목록 (기본 LATEST)
  '?sortType=LATEST',
  '?sortType=PRICE_ASC',
  '?sortType=PRICE_DESC',
  '?page=0&size=10',
  '?page=0&size=20',
  '?minPrice=10000&maxPrice=500000',
  '?minPrice=50000&maxPrice=2000000',
  '?sortType=PRICE_ASC&page=0&size=10',
];

export const options = {
  scenarios: {
    // Stage 1: cold — 1 VU, 1 iteration per param (캐시 미스 측정)
    cold_cache: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: SEARCH_PARAMS.length,
      startTime: '0s',
      exec: 'coldTest',
      tags: { phase: 'cold' },
    },
    // Stage 2: warm — 8 VU, 30s (캐시 히트 측정)
    warm_cache: {
      executor: 'constant-vus',
      vus: 8,
      duration: '30s',
      startTime: '10s',   // cold 완료 후 시작
      exec: 'warmTest',
      tags: { phase: 'warm' },
    },
  },
  thresholds: {
    // 캐시 히트가 미스보다 빠른지 수치로 확인 (발표용 — 절대값 기준이 아닌 비교 목적)
    cache_cold_duration: ['p(95)<3000'],
    cache_warm_duration: ['p(95)<100'],
  },
};

// Stage 1: 각 검색 파라미터로 첫 번째 요청 (캐시 미스)
export function coldTest() {
  const params = SEARCH_PARAMS[__ITER % SEARCH_PARAMS.length];
  const url = `${BASE_URL}/api/products${params}`;

  const res = http.get(url);
  coldDuration.add(res.timings.duration);

  check(res, { 'cold: status 200': (r) => r.status === 200 });
  sleep(0.5);
}

// Stage 2: 동일 파라미터 반복 요청 (캐시 히트)
export function warmTest() {
  const params = SEARCH_PARAMS[__ITER % SEARCH_PARAMS.length];
  const url = `${BASE_URL}/api/products${params}`;

  const res = http.get(url);
  warmDuration.add(res.timings.duration);

  check(res, { 'warm: status 200': (r) => r.status === 200 });
  sleep(0.2);
}
