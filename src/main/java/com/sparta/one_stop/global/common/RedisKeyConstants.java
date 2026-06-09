package com.sparta.one_stop.global.common;

public class RedisKeyConstants {
    public static final String REFRESH_TOKEN_PREFIX = "RT:";
    public static final String BLACKLIST_PREFIX = "BLACKLIST:";
    // user-level 토큰 무효화 cutoff (비번변경/탈퇴/정지 시 이 시각 이전 발급 AT 전부 거부)
    public static final String USER_TOKEN_CUTOFF_PREFIX = "UINVAL:";
}
