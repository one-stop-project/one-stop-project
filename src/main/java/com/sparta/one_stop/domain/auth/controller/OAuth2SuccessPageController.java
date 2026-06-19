package com.sparta.one_stop.domain.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 배포 환경에서 OAuth2 성공 여부를 확인하기 위한 임시 성공 페이지.
 *
 * SuccessHandler가 app.oauth2.success-redirect-uri로 code를 붙여 redirect한다.
 * 이후 프론트가 배포되면 success-redirect-uri를 프론트 콜백 주소로 교체하면 된다.
 */
@RestController
public class OAuth2SuccessPageController {

    @GetMapping(value = "/oauth2/success", produces = MediaType.TEXT_HTML_VALUE)
    public String success(@RequestParam(value = "code", required = false) String code) {
        String safeCode = code == null ? "" : code;
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
                        max-width: 760px;
                        margin: 0 auto;
                        background: white;
                        border: 1px solid #e2e8f0;
                        border-radius: 16px;
                        padding: 32px;
                        box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
                    }
                    h1 { margin-top: 0; color: #2563eb; }
                    code {
                        display: inline-block;
                        max-width: 100%;
                        word-break: break-all;
                        background: #f1f5f9;
                        padding: 4px 8px;
                        border-radius: 6px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>OAuth2 로그인 성공</h1>
                    <p>카카오 OAuth2 인증이 서버 도메인 기준으로 완료되었습니다.</p>
                    <p>발급된 1회용 교환 코드:</p>
                    <p><code>%s</code></p>
                    <p>Access Token 교환 API:</p>
                    <p><code>POST /api/auth/oauth2/exchange</code></p>
                    <p>브라우저 개발자 도구에서 <code>refresh_token</code>, <code>device_id</code> 쿠키가 내려갔는지도 확인하세요.</p>
                </div>
            </body>
            </html>
            """.formatted(safeCode);
    }
}
