-- LEGACY SAMPLE ONLY. Do not apply.
-- Active schema: V20260621__security_audit_v2.sql and later migrations.
-- ════════════════════════════════════════════════════════════════
--  보안 감사 로그 DB 스키마 (MySQL 8.0+)
--  파일명: V20250602__create_security_audit_log.sql (Flyway 컨벤션)
-- ════════════════════════════════════════════════════════════════


-- ─────────────────────────────────────────────────────────────
--  1. security_audit_log 테이블
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS security_audit_log (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,

    -- 이벤트 식별
    event_type          VARCHAR(50)     NOT NULL  COMMENT '이벤트 유형 (SecurityAuditEventType)',
    severity            VARCHAR(10)     NOT NULL  COMMENT 'CRITICAL/HIGH/MEDIUM/INFO',
    category            VARCHAR(10)     NOT NULL  COMMENT 'AUTH/AUTHZ/SELLER/POINT/ADMIN/OTHER',

    -- 주체 (Actor)
    actor_user_id       BIGINT          NULL      COMMENT '행위자 사용자 ID (비인증 요청은 NULL)',
    actor_email         VARCHAR(100)    NULL      COMMENT '행위자 이메일 (사용자 삭제 후에도 유지)',
    actor_role          VARCHAR(20)     NULL      COMMENT 'BUYER/SELLER/ADMIN/SUPER_ADMIN',

    -- 대상 (Target)
    target_resource     VARCHAR(30)     NULL      COMMENT '대상 리소스 종류 (Product/User 등)',
    target_id           VARCHAR(50)     NULL      COMMENT '대상 ID (문자열로 통일 — UUID 호환)',

    -- 결과
    result              VARCHAR(10)     NOT NULL  COMMENT 'SUCCESS/FAILURE/BLOCKED/DETECTED',
    error_code          VARCHAR(30)     NULL      COMMENT 'ErrorCode enum 이름',
    error_message       VARCHAR(500)    NULL,

    -- 컨텍스트
    method_name         VARCHAR(200)    NULL,
    method_args         VARCHAR(500)    NULL,
    client_ip           VARCHAR(45)     NULL      COMMENT 'IPv6 호환 길이',
    user_agent          VARCHAR(255)    NULL,
    request_uri         VARCHAR(255)    NULL,
    trace_id            VARCHAR(64)     NULL      COMMENT '분산 추적 ID',
    metadata            TEXT            NULL      COMMENT '추가 정보 JSON',

    -- 타임스탬프
    occurred_at         TIMESTAMP(6)    NOT NULL  COMMENT '이벤트 발생 시각',
    created_at          TIMESTAMP(6)    NOT NULL  DEFAULT CURRENT_TIMESTAMP(6)  COMMENT '레코드 생성 시각',

    PRIMARY KEY (id),
    INDEX idx_audit_event_time     (event_type, occurred_at DESC),
    INDEX idx_audit_user_time      (actor_user_id, occurred_at DESC),
    INDEX idx_audit_severity_time  (severity, occurred_at DESC),
    INDEX idx_audit_result_time    (result, occurred_at DESC),
    INDEX idx_audit_ip_time        (client_ip, occurred_at DESC),
    INDEX idx_audit_time_only      (occurred_at DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='통합 보안 감사 로그';


-- ─────────────────────────────────────────────────────────────
--  2. UPDATE/DELETE 차단 트리거 — 한 번 기록되면 변경 불가
--     (감사 로그의 핵심 — Immutable Audit Trail)
-- ─────────────────────────────────────────────────────────────
DROP TRIGGER IF EXISTS trg_security_audit_block_update;

DELIMITER $$

CREATE TRIGGER trg_security_audit_block_update
BEFORE UPDATE ON security_audit_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'security_audit_log is immutable — UPDATE is forbidden';
END$$

DELIMITER ;


DROP TRIGGER IF EXISTS trg_security_audit_block_delete;

DELIMITER $$

CREATE TRIGGER trg_security_audit_block_delete
BEFORE DELETE ON security_audit_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'security_audit_log is immutable — DELETE is forbidden. Use archive process.';
END$$

DELIMITER ;


-- ─────────────────────────────────────────────────────────────
--  3. 운영 환경 - 월 단위 파티셔닝 (선택)
--
--  대용량 환경에서 권장. 90일 이상은 archive 테이블로 이전.
-- ─────────────────────────────────────────────────────────────

-- 파티셔닝 적용 예시 (운영 환경에서만 사용)
/*
ALTER TABLE security_audit_log
PARTITION BY RANGE (TO_DAYS(occurred_at)) (
    PARTITION p202506 VALUES LESS THAN (TO_DAYS('2025-07-01')),
    PARTITION p202507 VALUES LESS THAN (TO_DAYS('2025-08-01')),
    PARTITION p202508 VALUES LESS THAN (TO_DAYS('2025-09-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
*/

-- ─────────────────────────────────────────────────────────────
--  4. 분석용 쿼리 모음
-- ─────────────────────────────────────────────────────────────

-- 최근 24시간 CRITICAL 이벤트
/*
SELECT * FROM security_audit_log
WHERE severity = 'CRITICAL'
  AND occurred_at > DATE_SUB(NOW(), INTERVAL 1 DAY)
ORDER BY occurred_at DESC;
*/

-- IP별 로그인 실패 TOP 10 (최근 1시간)
/*
SELECT client_ip, COUNT(*) cnt, MAX(occurred_at) last_seen
FROM security_audit_log
WHERE event_type IN ('LOGIN_FAILED_BAD_CREDENTIALS', 'LOGIN_FAILED_USER_NOT_FOUND')
  AND occurred_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY client_ip
ORDER BY cnt DESC
LIMIT 10;
*/

-- 사용자별 권한 위반 TOP 10 (최근 7일)
/*
SELECT actor_user_id, actor_email, COUNT(*) cnt
FROM security_audit_log
WHERE event_type IN ('ACCESS_DENIED', 'IDOR_ATTEMPT', 'PRIVILEGE_ESCALATION_ATTEMPT')
  AND occurred_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY actor_user_id, actor_email
ORDER BY cnt DESC
LIMIT 10;
*/

-- 카테고리별 이벤트 수 (지난주)
/*
SELECT category, COUNT(*) cnt
FROM security_audit_log
WHERE occurred_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY category
ORDER BY cnt DESC;
*/

-- Seller IDOR 시도 추적
/*
SELECT actor_user_id, target_id, occurred_at, metadata
FROM security_audit_log
WHERE event_type = 'SELLER_FOREIGN_PRODUCT_ACCESS'
  AND occurred_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY occurred_at DESC;
*/
