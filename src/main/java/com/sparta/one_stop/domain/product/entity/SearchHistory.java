package com.sparta.one_stop.domain.product.entity;

import com.sparta.one_stop.domain.user.entity.User;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_history",
        indexes = {
                @Index(name = "idx_sh_searched_at", columnList = "searched_at"),
                @Index(name = "idx_sh_keyword", columnList = "keyword, searched_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_id")
    private Long id;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    @Builder
    private SearchHistory(String keyword, User user) {
        this.keyword = keyword;
        this.user = user;
        this.searchedAt = LocalDateTime.now();
    }
}
