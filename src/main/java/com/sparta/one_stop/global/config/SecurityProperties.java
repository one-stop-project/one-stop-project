package com.sparta.one_stop.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Set;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
    Set<String> trustedProxies
) {
    // 💡 컴팩트 생성자: Null-Safe 처리 및 외부 조작을 막는 방어적 복사(Immutable) 적용
    public SecurityProperties {
        trustedProxies = (trustedProxies == null)
            ? Set.of()
            : Set.copyOf(trustedProxies);
    }
}
