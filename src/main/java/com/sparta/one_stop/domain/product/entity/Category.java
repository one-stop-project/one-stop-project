package com.sparta.one_stop.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.sparta.one_stop.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category",
        indexes = {
                @Index(name = "idx_category_parent_name", columnList = "parent_id, name")
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
