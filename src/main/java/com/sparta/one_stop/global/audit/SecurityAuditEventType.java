package com.sparta.one_stop.global.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SecurityAuditEventType {
    LOGIN_SUCCESS(Category.AUTH, SecuritySeverity.LOW, false),
    LOGIN_FAILED(Category.AUTH, SecuritySeverity.MEDIUM, false),
    LOGIN_FAILED_BAD_CREDENTIALS(Category.AUTH, SecuritySeverity.MEDIUM, false),
    LOGIN_FAILED_ACCOUNT_LOCKED(Category.AUTH, SecuritySeverity.HIGH, true),
    LOGIN_FAILED_USER_NOT_FOUND(Category.AUTH, SecuritySeverity.MEDIUM, false),
    LOGIN_FAIL_BURST_DETECTED(Category.AUTH, SecuritySeverity.HIGH, true),
    LOGIN_BLOCKED_SUSPENDED(Category.AUTH, SecuritySeverity.HIGH, true),
    USER_SUSPENSION_EXPIRED_AUTO_RELEASED(Category.AUTH, SecuritySeverity.MEDIUM, false),
    TOKEN_REFRESH_SUCCESS(Category.AUTH, SecuritySeverity.LOW, false),
    TOKEN_REFRESH_FAILED(Category.AUTH, SecuritySeverity.MEDIUM, false),
    REFRESH_TOKEN_REUSE_DETECTED(Category.AUTH, SecuritySeverity.CRITICAL, true),
    TOKEN_DEVICE_MISMATCH(Category.AUTH, SecuritySeverity.CRITICAL, true),
    TOKEN_EXPIRED(Category.AUTH, SecuritySeverity.LOW, false),
    TOKEN_INVALID_SIGNATURE(Category.AUTH, SecuritySeverity.CRITICAL, true),
    TOKEN_BLACKLISTED(Category.AUTH, SecuritySeverity.HIGH, true),
    LOGOUT(Category.AUTH, SecuritySeverity.LOW, false),
    RATE_LIMIT_BLOCKED(Category.AUTH, SecuritySeverity.HIGH, true),
    NEW_DEVICE_REGISTERED(Category.AUTH, SecuritySeverity.MEDIUM, false),
    DEVICE_LIMIT_EXCEEDED(Category.AUTH, SecuritySeverity.MEDIUM, true),
    SIGNUP_SUCCESS(Category.AUTH, SecuritySeverity.LOW, false),
    SIGNUP_FAILED_DUPLICATE(Category.AUTH, SecuritySeverity.MEDIUM, false),
    PASSWORD_CHANGED(Category.AUTH, SecuritySeverity.MEDIUM, false),
    ACCOUNT_WITHDRAWN(Category.AUTH, SecuritySeverity.MEDIUM, false),
    OAUTH2_LOGIN_SUCCESS(Category.AUTH, SecuritySeverity.LOW, false),
    OAUTH2_LOGIN_FAILED(Category.AUTH, SecuritySeverity.MEDIUM, false),
    ACCESS_DENIED(Category.AUTHZ, SecuritySeverity.MEDIUM, false),
    PRIVILEGE_ESCALATION_ATTEMPT(Category.AUTHZ, SecuritySeverity.CRITICAL, true),
    IDOR_ATTEMPT(Category.AUTHZ, SecuritySeverity.HIGH, true),
    ADMIN_API_ACCESS(Category.AUTHZ, SecuritySeverity.MEDIUM, false),
    SUPER_ADMIN_API_ACCESS(Category.AUTHZ, SecuritySeverity.HIGH, false),
    SELLER_PRODUCT_CREATE(Category.SELLER, SecuritySeverity.LOW, false),
    SELLER_PRODUCT_UPDATE(Category.SELLER, SecuritySeverity.LOW, false),
    SELLER_PRODUCT_DELETE(Category.SELLER, SecuritySeverity.MEDIUM, false),
    SELLER_FOREIGN_PRODUCT_ACCESS(Category.SELLER, SecuritySeverity.HIGH, true),
    SELLER_INVENTORY_INBOUND(Category.SELLER, SecuritySeverity.MEDIUM, false),
    SELLER_INVENTORY_ADJUSTMENT(Category.SELLER, SecuritySeverity.MEDIUM, false),
    SELLER_INVENTORY_MAX_EXCEEDED(Category.SELLER, SecuritySeverity.HIGH, true),
    SELLER_STATUS_CHANGE(Category.SELLER, SecuritySeverity.MEDIUM, false),
    POINT_CHARGE(Category.POINT, SecuritySeverity.MEDIUM, false),
    POINT_USE(Category.POINT, SecuritySeverity.MEDIUM, false),
    POINT_REFUND(Category.POINT, SecuritySeverity.MEDIUM, false),
    POINT_EARN(Category.POINT, SecuritySeverity.LOW, false),
    POINT_EXPIRE(Category.POINT, SecuritySeverity.LOW, false),
    POINT_INTEGRITY_VIOLATION(Category.POINT, SecuritySeverity.CRITICAL, true),
    POINT_INSUFFICIENT(Category.POINT, SecuritySeverity.LOW, false),
    POINT_ABNORMAL_USAGE(Category.POINT, SecuritySeverity.HIGH, true),
    ADMIN_USER_SUSPEND(Category.ADMIN, SecuritySeverity.HIGH, true),
    ADMIN_USER_REACTIVATE(Category.ADMIN, SecuritySeverity.HIGH, false),
    ADMIN_SELLER_APPROVE(Category.ADMIN, SecuritySeverity.MEDIUM, false),
    ADMIN_SELLER_REJECT(Category.ADMIN, SecuritySeverity.MEDIUM, false),
    ADMIN_DATA_EXPORT(Category.ADMIN, SecuritySeverity.HIGH, false),
    ADMIN_CONFIG_CHANGE(Category.ADMIN, SecuritySeverity.HIGH, false),
    USER_SUSPENDED(Category.ADMIN, SecuritySeverity.HIGH, true),
    USER_UNSUSPENDED(Category.ADMIN, SecuritySeverity.HIGH, false),
    USER_FORCE_LOGOUT(Category.ADMIN, SecuritySeverity.HIGH, true),
    SECURITY_TARGET_VIEWED(Category.ADMIN, SecuritySeverity.MEDIUM, false),
    SECURITY_AUDIT_LOG_VIEWED(Category.ADMIN, SecuritySeverity.MEDIUM, false),
    SECURITY_AUDIT_IP_DECRYPTED(Category.ADMIN, SecuritySeverity.HIGH, true),
    SUSPICIOUS_PATTERN_DETECTED(Category.OTHER, SecuritySeverity.HIGH, true),
    UNKNOWN_ERROR(Category.OTHER, SecuritySeverity.MEDIUM, false);

    private final Category category;
    private final SecuritySeverity severity;
    private final boolean suspiciousByDefault;

    public enum Category { AUTH, AUTHZ, SELLER, POINT, ADMIN, OTHER }
}
