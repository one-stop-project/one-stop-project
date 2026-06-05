package com.sparta.one_stop.global.alert.slack;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "slack.alert")
public class SlackAlertProperties {

    /**
     * Slack 알림 활성화 여부
     * - local 기본값: false
     * - 운영 또는 알림 테스트 환경에서 true로 설정
     */
    private boolean enabled = false;

    /**
     * Slack Incoming Webhook URL
     * - 환경변수 SLACK_ALERT_WEBHOOK_URL로 주입
     * - Git에 직접 커밋 금지
     */
    private String webhookUrl;

    public boolean hasWebhookUrl() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

}
