package com.sparta.one_stop.domain.product.support;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 상품 검색 FULLTEXT 인덱스 자동 생성 (#203 검색 의존).
// ddl-auto(update)는 FULLTEXT 인덱스를 만들지 않고, WITH PARSER ngram은 JPA로 표현 불가 →
// 앱 기동 시 인덱스가 없으면 직접 생성한다(있으면 건너뜀). DB 재생성·새 환경에서도 검색이 깨지지 않게 보장.
// test 프로필은 H2(ngram FULLTEXT 미지원)라 제외.
@Component
@Profile("!test")
@RequiredArgsConstructor
public class FulltextIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(FulltextIndexInitializer.class);
    private static final String INDEX_NAME = "idx_product_name_fulltext";

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureFulltextIndex() {
        try {
            if (indexExists()) {
                return;  // 이미 존재 — MySQL은 FULLTEXT IF NOT EXISTS 미지원이라 재생성 금지
            }
            jdbcTemplate.execute(
                "ALTER TABLE product ADD FULLTEXT INDEX " + INDEX_NAME
                    + " (name, description) WITH PARSER ngram");
            log.info("[Fulltext] {} 생성 완료", INDEX_NAME);
        } catch (Exception e) {
            // 다중 인스턴스 동시 기동 시 다른 쪽이 먼저 생성하면 중복 오류가 날 수 있음 → 재확인 후 분류.
            // 재확인(indexExists) 자체가 또 실패해도 기동을 막지 않도록 try-catch로 감싼다.
            try {
                if (indexExists()) {
                    log.info("[Fulltext] {} 이미 생성됨 (다른 인스턴스)", INDEX_NAME);
                } else {
                    // 생성 실패해도 기동은 막지 않음(검색만 영향) — 로그로 알림
                    log.error("[Fulltext] 인덱스 확인/생성 실패 — 검색이 동작하지 않을 수 있음", e);
                }
            } catch (Exception recheckEx) {
                log.error("[Fulltext] 인덱스 재확인 실패 — 검색이 동작하지 않을 수 있음", recheckEx);
            }
        }
    }

    private boolean indexExists() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'product' AND index_name = ?",
            Integer.class, INDEX_NAME);
        return cnt != null && cnt > 0;
    }
}
