-- search_history 멱등키 event_id (#363)
-- 목적: ack(LTRIM) 실패로 같은 batch가 재처리될 때 중복 INSERT 방지.
-- local(ddl-auto=update)은 엔티티 매핑으로 자동 생성되나, 배포(validate/none) DB는 이 스크립트를 수동 적용한다.
-- 레거시(컬럼 도입 전) 행은 event_id=NULL이며, MySQL UNIQUE는 다중 NULL을 허용하므로 제약 추가에 문제 없다.

ALTER TABLE search_history
    ADD COLUMN event_id VARCHAR(36) NULL,
    ADD CONSTRAINT uk_sh_event_id UNIQUE (event_id);
