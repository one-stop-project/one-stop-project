-- ════════════════════════════════════════════════════════════════
--  포인트 도메인 DB 트리거 (MySQL 8.0+)
--
--  설계 목적
--    1) 애플리케이션 외부에서 DB를 직접 UPDATE하는 행위를 감지
--    2) 비정상 변경 패턴을 audit_log에 기록
--    3) 운영 DBA가 잘못 만지는 경우도 추적 가능하도록
--
--  ⚠️ 주의
--    - Spring 애플리케이션의 정상 변경도 트리거를 거치므로 성능 영향 최소화 필수
--    - INSERT INTO ... LOG 같은 단순 작업만 (SELECT 등 무거운 작업 X)
--    - audit_log 테이블은 파티셔닝 권장 (월 단위)
-- ════════════════════════════════════════════════════════════════


-- ─────────────────────────────────────────────────────────────
--  1. 감사 로그 테이블
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS point_audit_log (
    audit_id        BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    point_id        BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    action          VARCHAR(20)     NOT NULL  COMMENT 'INSERT/UPDATE/DELETE',
    old_balance     INT             NULL,
    new_balance     INT             NULL,
    old_version     INT             NULL,
    new_version     INT             NULL,
    db_user         VARCHAR(100)    NOT NULL  COMMENT 'CURRENT_USER() — DB 접속 계정',
    client_host     VARCHAR(100)    NULL      COMMENT '@@hostname',
    occurred_at     TIMESTAMP(6)    NOT NULL  DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_audit_point  (point_id, occurred_at DESC),
    INDEX idx_audit_user   (user_id, occurred_at DESC),
    INDEX idx_audit_time   (occurred_at DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='포인트 변경 감사 로그 — 외부 조작 감지용';


-- ─────────────────────────────────────────────────────────────
--  2. UPDATE 트리거
--     모든 UPDATE를 기록 (정상 + 비정상 모두)
--     사후 분석 시 정상 흐름과 비정상 흐름을 구분
-- ─────────────────────────────────────────────────────────────
DROP TRIGGER IF EXISTS trg_points_audit_update;

DELIMITER $$

CREATE TRIGGER trg_points_audit_update
AFTER UPDATE ON points
FOR EACH ROW
BEGIN
    -- 잔액이 실제로 변경된 경우만 기록 (해시 컬럼만 바뀌는 경우 등 제외 가능)
    IF OLD.balance <> NEW.balance OR OLD.version <> NEW.version THEN
        INSERT INTO point_audit_log (
            point_id, user_id, action,
            old_balance, new_balance,
            old_version, new_version,
            db_user, client_host
        ) VALUES (
            NEW.point_id, NEW.user_id, 'UPDATE',
            OLD.balance, NEW.balance,
            OLD.version, NEW.version,
            CURRENT_USER(),
            @@hostname
        );
    END IF;
END$$

DELIMITER ;


-- ─────────────────────────────────────────────────────────────
--  3. DELETE 트리거 — 포인트 행 삭제는 절대 일어나서는 안 됨
--     발생 시 강제 차단 (SIGNAL로 예외 발생)
-- ─────────────────────────────────────────────────────────────
DROP TRIGGER IF EXISTS trg_points_block_delete;

DELIMITER $$

CREATE TRIGGER trg_points_block_delete
BEFORE DELETE ON points
FOR EACH ROW
BEGIN
    -- 일단 감사 로그 남기고
    INSERT INTO point_audit_log (
        point_id, user_id, action,
        old_balance, new_balance,
        old_version, new_version,
        db_user, client_host
    ) VALUES (
        OLD.point_id, OLD.user_id, 'DELETE_BLOCKED',
        OLD.balance, NULL,
        OLD.version, NULL,
        CURRENT_USER(),
        @@hostname
    );

    -- 그 다음 강제 차단
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'Points table DELETE is forbidden — use soft mechanism';
END$$

DELIMITER ;


-- ─────────────────────────────────────────────────────────────
--  4. point_history 감사 (선택)
--     UPDATE/DELETE 차단 — 한 번 기록된 이력은 절대 변경 불가
-- ─────────────────────────────────────────────────────────────
DROP TRIGGER IF EXISTS trg_point_history_block_update;

DELIMITER $$

CREATE TRIGGER trg_point_history_block_update
BEFORE UPDATE ON point_history
FOR EACH ROW
BEGIN
    -- 잔여 포인트(remaining_amount) 변경은 정상 흐름 (사용/만료 시)
    -- 그 외 컬럼이 바뀌면 비정상
    IF OLD.amount <> NEW.amount
       OR OLD.type <> NEW.type
       OR OLD.user_id <> NEW.user_id
       OR OLD.point_id <> NEW.point_id
       OR OLD.created_at <> NEW.created_at
       OR (OLD.expire_at IS NOT NULL OR NEW.expire_at IS NOT NULL) AND NOT (OLD.expire_at <> NEW.expire_at)
    THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'PointHistory immutable fields modification is forbidden';
    END IF;
END$$

DELIMITER ;


DROP TRIGGER IF EXISTS trg_point_history_block_delete;

DELIMITER $$

CREATE TRIGGER trg_point_history_block_delete
BEFORE DELETE ON point_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'PointHistory DELETE is forbidden — immutable audit trail';
END$$

DELIMITER ;


-- ════════════════════════════════════════════════════════════════
--  검증 쿼리 (감사 로그 분석용)
-- ════════════════════════════════════════════════════════════════

-- 최근 24시간 비정상 패턴 탐지: 짧은 시간에 큰 잔액 변동
-- (변동률 50% 이상 + 절대값 100,000원 이상)
/*
SELECT *
FROM point_audit_log
WHERE occurred_at > DATE_SUB(NOW(), INTERVAL 1 DAY)
  AND ABS(new_balance - old_balance) > 100000
  AND (old_balance = 0 OR ABS(new_balance - old_balance) / old_balance > 0.5)
ORDER BY occurred_at DESC;
*/

-- 의심스러운 DB 계정으로 변경된 이력 (app_user 외)
/*
SELECT db_user, COUNT(*) cnt, MIN(occurred_at) first_seen, MAX(occurred_at) last_seen
FROM point_audit_log
WHERE db_user NOT LIKE 'app_%'
GROUP BY db_user
ORDER BY cnt DESC;
*/

-- 특정 사용자의 변경 이력 추적
/*
SELECT * FROM point_audit_log WHERE user_id = ? ORDER BY occurred_at DESC LIMIT 100;
*/
