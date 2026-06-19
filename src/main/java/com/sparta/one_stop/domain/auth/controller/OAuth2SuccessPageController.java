package com.sparta.one_stop.domain.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 배포 환경에서 OAuth2 성공 여부를 확인하기 위한 임시 성공 페이지.
 *
 * 주의:
 * - 인증 교환 코드, 토큰, 쿠키 값 등 민감정보를 화면에 출력하지 않는다.
 * - 프론트엔드가 배포되면 app.oauth2.success-redirect-uri를 프론트 콜백 주소로 변경한다.
 */
@RestController
public class OAuth2SuccessPageController {

    @GetMapping(value = "/oauth2/success", produces = MediaType.TEXT_HTML_VALUE)
    public String success() {
        return """
            <!doctype html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <title>OAuth2 Login Success</title>
                <style>
                    body {
                        font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                        background: #f8fafc;
                        color: #0f172a;
                        padding: 48px;
                    }
                    .card {
                        max-width: 720px;
                        margin: 0 auto;
                        background: white;
                        border: 1px solid #e2e8f0;
                        border-radius: 16px;
                        padding: 32px;
                        box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
                    }
                    h1 { margin-top: 0; color: #2563eb; }
                    code {
                        background: #f1f5f9;
                        padding: 2px 6px;
                        border-radius: 6px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>OAuth2 로그인 성공</h1>
                    <p>카카오 OAuth2 인증이 서버 도메인 기준으로 완료되었습니다.</p>
                    <p>이 페이지는 서버 테스트용 임시 성공 페이지입니다.</p>
                    <p>인증 코드, 토큰, 쿠키 값은 보안상 화면에 표시하지 않습니다.</p>
                    <p>프론트 배포 후에는 <code>app.oauth2.success-redirect-uri</code>를 프론트 콜백 주소로 변경하세요.</p>
                </div>
            </body>
            </html>
            """;
    }
}
