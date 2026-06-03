package com.sparta.one_stop.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

// MySQL FULLTEXT 검색을 HQL/QueryDSL에서 호출할 수 있도록 커스텀 함수 등록
// function('fulltext_match', name, description, keyword) → match(name, description) against (keyword in boolean mode)
// 반환값은 관련도 점수(Double) — 매칭 시 > 0
// 패턴 함수라 실제 호출이 없으면 SQL로 렌더링되지 않음 (H2 테스트 무영향)
// META-INF/services/org.hibernate.boot.model.FunctionContributor 로 등록됨
public class MySqlFulltextFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
            "fulltext_match",
            "match(?1, ?2) against (?3 in boolean mode)",
            functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.DOUBLE)
        );
    }
}
