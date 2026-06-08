package com.sparta.one_stop.domain.notification.controller;

import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.sse.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final SseConnectionManager sseConnectionManager;

    /**
     * SSE 알림 구독
     * - 인증된 사용자만 구독 가능하다
     * - 연결 성공 시 초기 이벤트(connect)를 전송한다
     * - 사용자당 1개의 연결만 유지하며, 재연결 시 기존 emitter를 교체한다
     * - 타임아웃: 30분
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
        @AuthenticationPrincipal AuthUser authUser
    ) {
        return sseConnectionManager.connect(authUser.userId());
    }

}
