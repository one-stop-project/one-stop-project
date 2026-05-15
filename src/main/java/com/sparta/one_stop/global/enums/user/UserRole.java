package com.sparta.one_stop.global.enums.user;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {

    BUYER("구매자"),
    SELLER("판매자"),
    ADMIN("관리자"),
    SUPER_ADMIN("최고관리자");

    private final String description;

}
