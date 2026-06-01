package com.sparta.one_stop.global.enums.admin;

public enum AdminActionType {
    APPROVE,        // 승인
    REJECT,         // 반려
    FORCE_INACTIVE, // 강제 비활성화
    REACTIVATE,     // 정지 해제
    GRANT_ADMIN,    // 관리자 권한 부여 (BUYER → ADMIN)
    REVOKE_ADMIN    // 관리자 권한 회수 (ADMIN → BUYER)
}
