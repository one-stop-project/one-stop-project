-- ════════════════════════════════════════════════════════════
--  PointHistory 불변 트리거 수정본 (CodeRabbit 리뷰 대응)
-- ════════════════════════════════════════════════════════════
--
-- 🐛 진단된 버그:
--   기존 트리거의 expire_at 변경 감지 로직이 의도와 반대로 동작.
--
--   케이스                  | 기존 결과 | 의도 결과
--   ─────────────────────  │ ───────  │ ───────
--   둘 다 NULL              | 허용 ✓    | 허용
--   NULL → '2026-12-31'    | 허용 ✗    | 차단
--   '2026-12-31' → NULL    | 허용 ✗    | 차단
--   '2026-01-01' → '12-31' | 허용 ✗    | 차단
--   둘 다 동일 값            | 차단 ✗    | 허용
--
-- ✅ 해결: MySQL의 NULL-safe 비교 연산자 <=> 사용
--   - A <=> B : A와 B가 같으면 1, 다르면 0 (NULL도 정상 비교)
--   - NOT (A <=> B) : A와 B가 다르면 1 (변경됨)
-- ════════════════════════════════════════════════════════════

DROP TRIGGER IF EXISTS trg_point_history_block_immutable_update;

DELIMITER $$

CREATE TRIGGER trg_point_history_block_immutable_update
BEFORE UPDATE ON point_history
FOR EACH ROW
BEGIN
    -- ★ NULL-safe 비교 (<=>) 사용
    --   "NOT (OLD <=> NEW)" = "두 값이 다르면 true"
    IF NOT (OLD.amount     <=> NEW.amount)
       OR NOT (OLD.type    <=> NEW.type)
       OR NOT (OLD.user_id <=> NEW.user_id)
       OR NOT (OLD.point_id <=> NEW.point_id)
       OR NOT (OLD.created_at <=> NEW.created_at)
       OR NOT (OLD.expire_at  <=> NEW.expire_at)
    THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'PointHistory immutable fields modification is forbidden';
    END IF;
END$$

DELIMITER ;

-- ════════════════════════════════════════════════════════════
--  검증 시나리오
-- ════════════════════════════════════════════════════════════

-- 시나리오 1: 동일 값 UPDATE — 허용되어야 함
/*
UPDATE point_history
SET amount = amount,           -- 동일 값
    remaining_amount = remaining_amount - 100  -- 가변 필드만 변경
WHERE id = 1;
-- 예상: 정상 실행 (가변 필드만 변경)
*/

-- 시나리오 2: 불변 필드 변경 — 차단되어야 함
/*
UPDATE point_history SET amount = 99999 WHERE id = 1;
-- 예상: ERROR 1644 'PointHistory immutable fields modification is forbidden'
*/

-- 시나리오 3: NULL → 값 변경 — 차단되어야 함
/*
UPDATE point_history SET expire_at = '2026-12-31' WHERE id = 1 AND expire_at IS NULL;
-- 예상: ERROR 1644 'PointHistory immutable fields modification is forbidden'
*/

-- 시나리오 4: 값 → NULL 변경 — 차단되어야 함
/*
UPDATE point_history SET expire_at = NULL WHERE id = 1 AND expire_at IS NOT NULL;
-- 예상: ERROR 1644 'PointHistory immutable fields modification is forbidden'
*/
