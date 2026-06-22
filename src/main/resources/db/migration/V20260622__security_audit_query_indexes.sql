CREATE INDEX idx_sal_severity_time
    ON security_audit_logs (severity, occurred_at);

CREATE INDEX idx_sal_time_category
    ON security_audit_logs (occurred_at, category);
