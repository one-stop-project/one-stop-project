-- 같은 부모 아래 카테고리명 중복 방지 — category UNIQUE 제약
-- ddl-auto(update)는 기존 테이블에 UNIQUE 제약을 추가하지 않으므로 MySQL에 수동 적용한다.
-- (신규 빈 DB는 엔티티 @UniqueConstraint로 생성 시 자동 반영됨)
-- ⚠️ MySQL은 (parent_id, name)에서 parent_id NULL(루트)을 다중 허용 → 루트 카테고리 중복은 막지 못한다(앱 레벨 검증으로 보장).
-- ⚠️ 적용 전, 기존에 (parent_id, name) 중복이 있으면 제약 추가가 실패한다. 먼저 1)로 확인 후 정리한다.
-- ⚠️ 이미 적용돼 있으면 재실행 금지 (MySQL은 ADD CONSTRAINT IF NOT EXISTS 미지원 → 재실행 시 'Duplicate key name' 오류).

-- 1) 기존 중복 확인 (행이 나오면 정리 후 2) 진행)
-- SELECT parent_id, name, COUNT(*) AS cnt
-- FROM category
-- GROUP BY parent_id, name
-- HAVING cnt > 1;

-- 2) 유니크 제약 추가
ALTER TABLE category
    ADD CONSTRAINT uk_category_parent_name
    UNIQUE (parent_id, name);
