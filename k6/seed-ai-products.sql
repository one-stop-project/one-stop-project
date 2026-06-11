-- =====================================================
-- K6 AI 엔드포인트 테스트용 더미 상품 데이터 시드
-- 네이버 API / LLM 없이 AI 엔드포인트 성능 테스트 가능하도록 직접 삽입
--
-- 포함 내용:
--   - AI 더미 셀러 (user + seller)
--   - 테스트 카테고리 3계층 (패션·전자제품)
--   - 상품 5개 (각 ProductItem / ProductImage / CategoryMapping 포함)
--   - product_review_summary 5개 (AI 요약 조회 즉시 동작)
--   - dummy_product_source_group (멱등 추적용)
--
-- 실행: USE one_stop_db; SOURCE k6/seed-ai-products.sql;
-- INSERT IGNORE: 이미 존재하면 건너뜀 (멱등)
-- ID 범위 90001+ 사용 — 실제 시드 데이터와 충돌 방지
-- =====================================================

-- ── 1. AI 더미 셀러 유저 ──────────────────────────────
-- DummySellerSeeder가 이미 실행됐으면 ai-shop@dummy.local 존재 → INSERT IGNORE로 건너뜀
-- 비밀번호: Test1234! (로그인 불필요라 값 무관)
INSERT IGNORE INTO users (email, password, name, role, status, created_at, updated_at) VALUES
('ai-shop@dummy.local', '$2a$10$aXcgWG4zdV7.Tyt8RD8fmu517jto9myfe61.HQY9hdG5LRsj3BC4S', 'AI더미샵', 'SELLER', 'ACTIVE', NOW(), NOW());

-- user_id 동적으로 가져오기 (이미 존재하는 경우 포함)
SET @ai_user_id = (SELECT user_id FROM users WHERE email = 'ai-shop@dummy.local');

INSERT IGNORE INTO seller (user_id, shop_name, business_number, status) VALUES
(@ai_user_id, 'AI더미샵', '000-00-00000', 'APPROVED');

-- seller_id 동적으로 가져오기
SET @ai_seller_id = (SELECT seller_id FROM seller WHERE user_id = @ai_user_id);

-- ── 2. 테스트 카테고리 (대 > 중 > 소) ────────────────
INSERT IGNORE INTO category (category_id, name, parent_id, created_at, updated_at) VALUES
-- 대카테고리
(90001, 'K6-패션', NULL, NOW(), NOW()),
(90002, 'K6-전자제품', NULL, NOW(), NOW()),
-- 중카테고리
(90003, 'K6-패션-의류', 90001, NOW(), NOW()),
(90004, 'K6-전자제품-노트북', 90002, NOW(), NOW()),
-- 소카테고리 (leaf)
(90005, 'K6-패션-의류-아우터', 90003, NOW(), NOW()),
(90006, 'K6-전자제품-노트북-울트라북', 90004, NOW(), NOW());

-- ── 3. 상품 5개 (status=APPROVED) ─────────────────────
INSERT IGNORE INTO product (product_id, seller_id, name, description, thumbnail_url,
                             option_name_1, option_name_2, option_name_3, option_name_4, option_name_5,
                             status, view_count, sales_count, version, deleted_at, created_at, updated_at) VALUES
(90001, @ai_seller_id, '[K6] 여성 캐시미어 롱코트',
 '고급 캐시미어 혼방 소재의 여성용 롱코트입니다. 보온성과 스타일을 동시에.',
 '/images/default-product.png',
 '색상', '사이즈', '', '', '',
 'APPROVED', 1500, 120, 0, NULL, NOW(), NOW()),

(90002, @ai_seller_id, '[K6] 남성 구스다운 패딩',
 '750fp 구스다운 충전재, 방수 처리된 아우터. 영하 20도에도 따뜻함.',
 '/images/default-product.png',
 '색상', '사이즈', '', '', '',
 'APPROVED', 2300, 380, 0, NULL, NOW(), NOW()),

(90003, @ai_seller_id, '[K6] 울트라북 14인치 노트북',
 '인텔 i7 13세대, 16GB RAM, 512GB SSD. 초경량 1.2kg 비즈니스 노트북.',
 '/images/default-product.png',
 '색상', 'RAM', 'SSD', '', '',
 'APPROVED', 4100, 55, 0, NULL, NOW(), NOW()),

(90004, @ai_seller_id, '[K6] 무선 블루투스 노이즈캔슬링 이어폰',
 '액티브 노이즈캔슬링, 30시간 재생. 프리미엄 사운드 경험.',
 '/images/default-product.png',
 '색상', '', '', '', '',
 'APPROVED', 3200, 210, 0, NULL, NOW(), NOW()),

(90005, @ai_seller_id, '[K6] 여성 오버사이즈 울 가디건',
 '100% 울 소재 오버사이즈 핏. 캐주얼과 포멀 모두 어울리는 아이템.',
 '/images/default-product.png',
 '색상', '사이즈', '', '', '',
 'APPROVED', 980, 67, 0, NULL, NOW(), NOW());

-- ── 4. 상품 옵션 (product_item) ───────────────────────
-- 상품 90001: 롱코트 (색상 × 사이즈)
INSERT IGNORE INTO product_item (product_id, option_value_1, option_value_2, option_value_3, option_value_4, option_value_5, price, stock, status, created_at, updated_at) VALUES
(90001, '블랙', 'S', '', '', '', 189000, 30, 'ON_SALE', NOW(), NOW()),
(90001, '블랙', 'M', '', '', '', 189000, 25, 'ON_SALE', NOW(), NOW()),
(90001, '블랙', 'L', '', '', '', 189000, 20, 'ON_SALE', NOW(), NOW()),
(90001, '베이지', 'S', '', '', '', 189000, 15, 'ON_SALE', NOW(), NOW()),
(90001, '베이지', 'M', '', '', '', 189000, 18, 'ON_SALE', NOW(), NOW()),
(90001, '베이지', 'L', '', '', '', 189000, 12, 'ON_SALE', NOW(), NOW());

-- 상품 90002: 구스다운 패딩
INSERT IGNORE INTO product_item (product_id, option_value_1, option_value_2, option_value_3, option_value_4, option_value_5, price, stock, status, created_at, updated_at) VALUES
(90002, '블랙', 'M', '', '', '', 298000, 40, 'ON_SALE', NOW(), NOW()),
(90002, '블랙', 'L', '', '', '', 298000, 35, 'ON_SALE', NOW(), NOW()),
(90002, '네이비', 'M', '', '', '', 298000, 22, 'ON_SALE', NOW(), NOW()),
(90002, '네이비', 'L', '', '', '', 298000, 18, 'ON_SALE', NOW(), NOW());

-- 상품 90003: 노트북
INSERT IGNORE INTO product_item (product_id, option_value_1, option_value_2, option_value_3, option_value_4, option_value_5, price, stock, status, created_at, updated_at) VALUES
(90003, '실버', '16GB', '512GB', '', '', 1590000, 10, 'ON_SALE', NOW(), NOW()),
(90003, '실버', '32GB', '1TB',   '', '', 1890000, 8,  'ON_SALE', NOW(), NOW()),
(90003, '스페이스그레이', '16GB', '512GB', '', '', 1590000, 12, 'ON_SALE', NOW(), NOW());

-- 상품 90004: 이어폰
INSERT IGNORE INTO product_item (product_id, option_value_1, option_value_2, option_value_3, option_value_4, option_value_5, price, stock, status, created_at, updated_at) VALUES
(90004, '블랙', '', '', '', '', 229000, 50, 'ON_SALE', NOW(), NOW()),
(90004, '화이트', '', '', '', '', 229000, 45, 'ON_SALE', NOW(), NOW());

-- 상품 90005: 울 가디건
INSERT IGNORE INTO product_item (product_id, option_value_1, option_value_2, option_value_3, option_value_4, option_value_5, price, stock, status, created_at, updated_at) VALUES
(90005, '아이보리', 'S', '', '', '', 89000, 20, 'ON_SALE', NOW(), NOW()),
(90005, '아이보리', 'M', '', '', '', 89000, 25, 'ON_SALE', NOW(), NOW()),
(90005, '그레이', 'M', '', '', '', 89000, 18, 'ON_SALE', NOW(), NOW()),
(90005, '그레이', 'L', '', '', '', 89000, 15, 'ON_SALE', NOW(), NOW());

-- ── 5. 상품 이미지 ────────────────────────────────────
INSERT IGNORE INTO product_image (product_id, image_url, display_order, status, created_at, updated_at) VALUES
(90001, '/images/default-product.png', 1, 'ACTIVE', NOW(), NOW()),
(90002, '/images/default-product.png', 1, 'ACTIVE', NOW(), NOW()),
(90003, '/images/default-product.png', 1, 'ACTIVE', NOW(), NOW()),
(90004, '/images/default-product.png', 1, 'ACTIVE', NOW(), NOW()),
(90005, '/images/default-product.png', 1, 'ACTIVE', NOW(), NOW());

-- ── 6. 카테고리 매핑 ──────────────────────────────────
-- 상품 90001~90002: 패션 > 의류 > 아우터 (소→중→대 3개 매핑)
INSERT IGNORE INTO product_category_mapping (product_id, category_id, created_at, updated_at) VALUES
(90001, 90005, NOW(), NOW()), -- 소: K6-패션-의류-아우터
(90001, 90003, NOW(), NOW()), -- 중: K6-패션-의류
(90001, 90001, NOW(), NOW()), -- 대: K6-패션
(90002, 90005, NOW(), NOW()),
(90002, 90003, NOW(), NOW()),
(90002, 90001, NOW(), NOW()),
(90005, 90005, NOW(), NOW()),
(90005, 90003, NOW(), NOW()),
(90005, 90001, NOW(), NOW());

-- 상품 90003~90004: 전자제품 > 노트북 > 울트라북
INSERT IGNORE INTO product_category_mapping (product_id, category_id, created_at, updated_at) VALUES
(90003, 90006, NOW(), NOW()), -- 소: K6-전자제품-노트북-울트라북
(90003, 90004, NOW(), NOW()), -- 중: K6-전자제품-노트북
(90003, 90002, NOW(), NOW()), -- 대: K6-전자제품
(90004, 90006, NOW(), NOW()),
(90004, 90004, NOW(), NOW()),
(90004, 90002, NOW(), NOW());

-- ── 7. AI 리뷰 요약 (product_review_summary) ─────────
-- review_count >= 5 여야 ai-summary API가 READY 상태로 응답함
-- AI가 실제로 생성할 법한 내용으로 작성 (k6 응답 검증에 도움)
INSERT IGNORE INTO product_review_summary
    (product_id, pros, cons, keywords, sentiment, last_included_review_id,
     review_count, average_rating, version, created_at, updated_at)
VALUES
(90001,
 '["보온성이 뛰어나다", "핏이 예쁘다", "소재가 부드럽다"]',
 '["가격이 비싸다", "세탁 주의 필요"]',
 '["캐시미어", "롱코트", "여성아우터", "보온", "고급"]',
 'POSITIVE', NULL, 38, 4.5, 0, NOW(), NOW()),

(90002,
 '["따뜻하다", "가벼우면서 보온성 좋음", "디자인이 깔끔하다"]',
 '["가격이 높다", "색상 선택지가 적다"]',
 '["구스다운", "패딩", "방수", "겨울아우터", "보온"]',
 'POSITIVE', NULL, 62, 4.7, 0, NOW(), NOW()),

(90003,
 '["가볍고 휴대성이 좋다", "배터리 지속시간이 길다", "화면이 선명하다"]',
 '["발열이 있다", "가격 대비 포트 수가 적다"]',
 '["노트북", "울트라북", "경량", "i7", "배터리"]',
 'POSITIVE', NULL, 29, 4.3, 0, NOW(), NOW()),

(90004,
 '["노이즈캔슬링이 탁월하다", "음질이 뛰어나다", "착용감이 편하다"]',
 '["통화 품질이 아쉽다", "충전 케이스 크기가 크다"]',
 '["이어폰", "노이즈캔슬링", "블루투스", "음질", "무선"]',
 'POSITIVE', NULL, 85, 4.6, 0, NOW(), NOW()),

(90005,
 '["소재가 고급스럽다", "오버사이즈 핏이 트렌디하다", "따뜻하다"]',
 '["세탁 후 줄어들 수 있다", "보풀 발생 가능성"]',
 '["가디건", "울", "오버사이즈", "여성의류", "캐주얼"]',
 'POSITIVE', NULL, 21, 4.4, 0, NOW(), NOW());

-- ── 8. 더미 소스 추적 (dummy_product_source_group) ────
-- 멱등 추적용 — 실제 Naver API 데이터 없이도 해당 product_id 인식
INSERT IGNORE INTO dummy_product_source_group (source, base_source_key, product_id, created_at, updated_at) VALUES
('NAVER', 'K6TEST|캐시미어롱코트|AI더미샵|여성의류아우터', 90001, NOW(), NOW()),
('NAVER', 'K6TEST|구스다운패딩|AI더미샵|남성아우터', 90002, NOW(), NOW()),
('NAVER', 'K6TEST|울트라북14인치|AI더미샵|노트북', 90003, NOW(), NOW()),
('NAVER', 'K6TEST|블루투스이어폰|AI더미샵|이어폰', 90004, NOW(), NOW()),
('NAVER', 'K6TEST|울가디건|AI더미샵|여성의류', 90005, NOW(), NOW());

-- ── 완료 확인 쿼리 ─────────────────────────────────────
-- SELECT p.product_id, p.name, prs.review_count, prs.average_rating, prs.sentiment
-- FROM product p
-- JOIN product_review_summary prs ON p.product_id = prs.product_id
-- WHERE p.product_id BETWEEN 90001 AND 90005;
