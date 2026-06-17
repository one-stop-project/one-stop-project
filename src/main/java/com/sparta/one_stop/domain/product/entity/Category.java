package com.sparta.one_stop.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.sparta.one_stop.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category",
        uniqueConstraints = {
                // 같은 부모 아래 카테고리명 중복 방지 (앱 레벨 검증 + DB 최후 방어선)
                // ⚠️ MySQL은 (parent_id, name)에서 parent_id NULL(루트)을 다중 허용하므로
                //    루트 카테고리 중복은 이 제약이 막지 못한다 → 루트는 앱 레벨 검증(+trim)만 보장
                @UniqueConstraint(name = "uk_category_parent_name", columnNames = {"parent_id", "name"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Builder
    private Category(String name, Category parent) {
        this.name = name;
        this.parent = parent;
    }

    public void updateName(String name) {
        if (name != null) this.name = name;
    }

    public boolean isRoot() {
        return this.parent == null;
    }

    public int getDepth() {
        int depth = 1;
        Category current = this.parent;
        while (current != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }
}
