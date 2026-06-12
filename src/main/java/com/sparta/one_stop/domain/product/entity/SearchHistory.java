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
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_sh_event_id", columnNames = "event_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_id")
    private Long id;

    // 재처리(ack 실패 후 같은 batch 재peek) 시 중복 INSERT를 막는 멱등키. 검색 적재 시점에 생성된다.
    // 레거시(컬럼 도입 전) 행은 null일 수 있어 nullable — MySQL UNIQUE는 다중 null을 허용한다.
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    // searchedAt이 null이면 호출 시점으로 폴백 — 5분 지연 batch INSERT에서 raw 시각 보존
    @Builder
    private SearchHistory(String eventId, String keyword, User user, LocalDateTime searchedAt) {
        this.eventId = eventId;
        this.keyword = keyword;
        this.user = user;
        this.searchedAt = (searchedAt != null) ? searchedAt : LocalDateTime.now();
    }
}
