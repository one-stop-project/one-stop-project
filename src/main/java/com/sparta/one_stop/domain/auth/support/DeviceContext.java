package com.sparta.one_stop.domain.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 기기 컨텍스트 해싱 — Level 2 보안 (Context Binding)
 *
 * 목적
 * deviceId(UUID) 하나만 믿지 않고, 접속 환경(OS·브라우저·IP 대역)을 함께 묶어
 * 해시로 저장한다. RT + deviceId가 탈취돼도 공격자의 접속 환경이 다르면 감지 가능.
 *
 * 설계 원칙 — false positive 최소화
 *
 *   ❌ 넣지 않는 것:
 *     - 전체 IP        → 모바일 셀룰러↔WiFi 전환마다 변동 → 정상 사용자 차단
 *     - UA 전체 문자열  → 브라우저 자동 업데이트만 해도 버전 변동 → 불일치
 *
 *   ✅ 넣는 것:
 *     - OS 종류         (WINDOWS / MACOS / IOS / ANDROID / LINUX)
 *     - 브라우저 종류    (CHROME / SAFARI / FIREFOX / EDGE …) — 버전 제외
 *     - IP 대역         (IPv4 /24, IPv6 /48) — 호스트 비트 제거
 *
 * 리팩터링 노트 (v2)
 *
 *   순수 함수 유틸 — 상태/부수효과 없음, 스레드 안전<
 *   MessageDigest는 호출 시마다 새 인스턴스 생성 (MessageDigest 자체가 thread-unsafe).
 *       단, getInstance 비용은 무시 가능 수준이며 공유 인스턴스는 동시성 버그를 유발하므로
 *       의도적으로 매 호출 생성한다.
 *   HexFormat 사용으로 hex 변환 단순화 (Java 17+)
 *   IPv6 /48 프리픽스 지원 추가
 *
 *
 * 불일치는 즉시 차단이 아니라 보안 이벤트 + 알림 트리거로 사용 권장.
 * (차단은 RT-deviceId 불일치 같은 더 강한 신호에서만)
 */
public final class DeviceContext {

    private static final HexFormat HEX = HexFormat.of();

    private DeviceContext() {
    }

    /**
     * 접속 컨텍스트 해시 생성 (순수 함수)
     *
     * @param deviceId  기기 식별자 (UUID, null 허용)
     * @param userAgent User-Agent 헤더 (null 허용)
     * @param clientIp  클라이언트 IP (null 허용)
     * @return SHA-256 해시 (hex 64자)
     */
    public static String hash(String deviceId, String userAgent, String clientIp) {
        String safeDeviceId = (deviceId == null) ? "" : deviceId;
        String osType = parseOsType(userAgent);
        String browserType = parseBrowserType(userAgent);
        String ipPrefix = toNetworkPrefix(clientIp);

        String raw = String.join("|", safeDeviceId, osType, browserType, ipPrefix);
        return sha256Hex(raw);
    }

    /** User-Agent에서 OS 종류만 추출 (버전 제외) */
    public static String parseOsType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "UNKNOWN_OS";
        }
        String ua = userAgent.toLowerCase();
        // 순서 중요: Android는 Linux 문자열도 포함 → 먼저 검사
        if (ua.contains("android")) return "ANDROID";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "IOS";
        if (ua.contains("windows")) return "WINDOWS";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "MACOS";
        if (ua.contains("linux")) return "LINUX";
        return "OTHER_OS";
    }

    /** User-Agent에서 브라우저 종류만 추출 (버전 제외) */
    public static String parseBrowserType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "UNKNOWN_BROWSER";
        }
        String ua = userAgent.toLowerCase();
        // 순서 중요: 파생 브라우저(Edge/Opera/Samsung)를 Chrome보다 먼저, Safari는 마지막
        if (ua.contains("edg/") || ua.contains("edge")) return "EDGE";
        if (ua.contains("opr/") || ua.contains("opera")) return "OPERA";
        if (ua.contains("samsungbrowser")) return "SAMSUNG";
        if (ua.contains("chrome") || ua.contains("crios")) return "CHROME";
        if (ua.contains("firefox") || ua.contains("fxios")) return "FIREFOX";
        if (ua.contains("safari")) return "SAFARI";
        return "OTHER_BROWSER";
    }

    /**
     * IP를 네트워크 프리픽스로 변환 (호스트 비트 제거)
     *
     *
     *   IPv4: 192.168.1.55           → 192.168.1.x      (/24)
     *   IPv6: 2001:db8:1:2:3:4:5:6   → 2001:db8:1:x     (/48, 앞 3그룹)
     *   파싱 불가/null               → NO_IP
     *
     *
     * 같은 네트워크 내 IP 변동은 허용하고, 완전히 다른 대역만 감지한다.
     */
    public static String toNetworkPrefix(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "NO_IP";
        }
        String ip = clientIp.trim();

        // IPv6 (콜론 포함) — 축약 표기(::)를 InetAddress로 정규화 후 앞 3그룹(/48)
        if (ip.contains(":")) {
            try {
                java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
                if (addr instanceof java.net.Inet6Address) {
                    byte[] b = addr.getAddress(); // 16바이트 풀 표현
                    // 앞 6바이트(3그룹 = /48)를 hex로
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 6; i += 2) {
                        if (i > 0) sb.append(":");
                        int group = ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
                        sb.append(Integer.toHexString(group));
                    }
                    return sb.append(":x").toString();
                }
                // IPv4-mapped 등은 아래 IPv4 처리로 흘려보냄
            } catch (Exception e) {
                return "NO_IP";
            }
        }

        // IPv4
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return "NO_IP";
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + ".x";
    }

    /**
     * 두 컨텍스트 해시 비교 (constant-time, 타이밍 공격 방어)
     */
    public static boolean matches(String hashA, String hashB) {
        if (hashA == null || hashB == null) {
            return false;
        }
        return MessageDigest.isEqual(
            hashA.getBytes(StandardCharsets.UTF_8),
            hashB.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String sha256Hex(String raw) {
        try {
            // MessageDigest는 thread-unsafe → 매 호출 새 인스턴스 (의도적)
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM에 존재 → 사실상 발생 안 함
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
