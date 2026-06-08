package com.sparta.one_stop.dummy.source;

import com.sparta.one_stop.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 더미 시드 멱등 — "원본 마켓의 기본 상품(변형 묶음)" ↔ 우리 Product 매핑.
// 재실행 시 baseSourceKey로 기존 그룹을 찾아 같은 Product에 변형(옵션)을 누적/갱신한다.
// 운영 Product 스키마를 더럽히지 않으려고 별도 테이블로 분리.
@Entity
@Table(name = "dummy_product_source_group",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dummy_source_group",
                columnNames = {"source", "base_source_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DummyProductSourceGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 출처 마켓 (현재 NAVER. 후속 마켓 어댑터 추가 대비 컬럼화)
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    // 기본 상품 식별 키 = 원본 raw 필드 기반(brand+maker+변형토큰 제거 title+카테고리).
    // LLM 생성물(이름·설명)은 재실행 매칭이 깨지므로 절대 포함하지 않는다.
    @Column(name = "base_source_key", nullable = false, length = 255)
    private String baseSourceKey;

    // 이 그룹으로 생성된 우리 Product id (운영 상품 생명주기와 분리하려 FK 아닌 단순 참조)
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Builder
    private DummyProductSourceGroup(String source, String baseSourceKey, Long productId) {
        this.source = source;
        this.baseSourceKey = baseSourceKey;
        this.productId = productId;
    }
}
