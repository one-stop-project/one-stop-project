package com.sparta.one_stop.domain.point.util;


import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
public class PointIntegrityHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private static PointIntegrityHasher INSTANCE;

    private final byte[] secretKey;

    public PointIntegrityHasher(
        @Value("${point.integrity.secret:CHANGE_ME_TO_LONG_RANDOM_STRING_AT_LEAST_32_BYTES}") String secret) {

        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // 운영 환경에서 키 조건 미 일치시 블락
            throw new IllegalStateException(
                "point.integrity.secret must be configured with a secure random string of at least 32 bytes");
        }
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostConstruct
    void init() {
        INSTANCE = this;
        log.info("PointIntegrityHasher initialized (secret length: {} bytes)", secretKey.length);
    }

    // 무결성 해시 생성
    // 64자 hex 문자열
    public static String hash(Long userId, Integer balance, Integer version) {
        if (INSTANCE == null) {
            throw new IllegalStateException("PointIntegrityHasher not initialized yet");
        }
        return INSTANCE.doHash(userId, balance, version);
    }

    // 무결성 검증 - 타이밍 공격 방어를 위한 constant-time 비교
    public static boolean verify(Long userId, Integer balance, Integer version, String expectHash) {
        if (expectHash == null) return false;
        String actual = hash(userId, balance, version);
        // MassageDigest.isEqual은 타이밍 공격 방어(early exit 안함)
        return MessageDigest.isEqual(
            actual.getBytes(StandardCharsets.UTF_8),
            expectHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    //doHash
    private String doHash(Long userId, Integer balance, Integer version) {
        if (userId == null || balance == null || version == null) {
            throw new CustomException(ErrorCode.POINT_010, "해시 생성 오류");
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, ALGORITHM));

            // 입력 포맷 : "userId|balance|version"
            String input = userId + "|" + balance + "|" + version;
            byte[] hashBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(hashBytes);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.POINT_010, "무결성 해시 생성 실패");
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
        }

}
