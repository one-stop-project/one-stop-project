-- 상품 옵션 조합 중복 방지 — product_item UNIQUE 제약
-- ddl-auto(update)는 기존 테이블에 UNIQUE 제약을 추가하지 않으므로 MySQL에 수동 적용한다.
-- (신규 빈 DB는 엔티티 @UniqueConstraint로 생성 시 자동 반영됨)
-- ⚠️ 적용 전, 기존에 중복 옵션 조합이 있으면 제약 추가가 실패한다. 먼저 1)로 확인 후 정리한다.
-- ⚠️ 이미 적용돼 있으면 재실행 금지 (MySQL은 ADD CONSTRAINT IF NOT EXISTS 미지원 → 재실행 시 'Duplicate key name' 오류).

-- 1) 기존 중복 확인 (행이 나오면 정리 후 2) 진행)
-- SELECT product_id, option_value_1, option_value_2, option_value_3, option_value_4, option_value_5, COUNT(*) AS cnt
-- FROM product_item
-- GROUP BY product_id, option_value_1, option_value_2, option_value_3, option_value_4, option_value_5
-- HAVING cnt > 1;

-- 2) 유니크 제약 추가
ALTER TABLE product_item
    ADD CONSTRAINT uk_product_item_options
    UNIQUE (product_id, option_value_1, option_value_2, option_value_3, option_value_4, option_value_5);
