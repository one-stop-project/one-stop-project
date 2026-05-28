package com.sparta.one_stop.global.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaForwardController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        // 원래 요청했던 URI를 가져옵니다.
        String originalUri = (String) request.getAttribute("jakarta.servlet.error.request_uri");

        // 1. /api 로 시작하는 백엔드 API 요청이 실패한 거라면 원래대로 404 에러 처리
        if (originalUri != null && originalUri.startsWith("/api/")) {
            return "error";
        }

        // 2. 그 외의 경로(React 화면 이동 등)에서 404가 났다면 index.html로 포워딩
        return "forward:/index.html";
    }
}
