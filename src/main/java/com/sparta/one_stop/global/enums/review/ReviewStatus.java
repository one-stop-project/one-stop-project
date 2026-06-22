package com.sparta.one_stop.global.enums.review;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewStatus {

    ACTIVE("활성"),
    DELETED("삭제");

    private final String description;
}
