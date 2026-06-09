package com.sparta.one_stop.global.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpExtractor {

    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP"
    };

    private ClientIpExtractor() {
    }

    /**
     * 요청에서 클라이언트 실제 IP 추출
     *
     * @param request HTTP 요청 (null 허용)
     * @return 추출된 IP. 추출 불가 시 "unknown"
     */
    public static String extract(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (isValid(ip)) {
                // X-Forwarded-For는 "client, proxy1, proxy2" 형태 → 첫 번째가 실제 클라이언트
                return ip.contains(",") ? ip.split(",")[0].trim() : ip.trim();
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
    }

    private static boolean isValid(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }
}

