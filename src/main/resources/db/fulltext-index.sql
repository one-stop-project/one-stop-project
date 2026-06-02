-- 상품 검색 FULLTEXT 인덱스 (#203)
-- ddl-auto(update)는 FULLTEXT 인덱스를 생성하지 않으므로 MySQL에 수동 적용한다.
-- 한글 토크나이징을 위해 ngram 파서 사용 (MySQL 5.7.6+/8.x 기본 탑재).
-- ngram 최소 토큰 크기(ngram_token_size, 기본 2)와 정책의 "검색어 2자 이상"이 일치한다.
-- 인덱스가 이미 있으면 재실행하지 말 것(MySQL은 FULLTEXT IF NOT EXISTS 미지원).

ALTER TABLE product
    ADD FULLTEXT INDEX idx_product_name_fulltext (name, description) WITH PARSER ngram;
