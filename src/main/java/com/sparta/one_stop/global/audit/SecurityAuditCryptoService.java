package com.sparta.one_stop.global.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class SecurityAuditCryptoService {
    private final byte[] hmacSecret;
    private final SecretKeySpec aesKey;
    private final String keyVersion;
    private final SecureRandom random = new SecureRandom();

    public SecurityAuditCryptoService(
        @Value("${security.audit.hmac-secret}") String hmacSecret,
        @Value("${security.audit.aes-secret}") String aesSecret,
        @Value("${security.audit.aes-key-version:v1}") String keyVersion
    ) {
        if (!StringUtils.hasText(hmacSecret) || !StringUtils.hasText(aesSecret))
            throw new IllegalStateException("보안 감사용 HMAC/AES secret은 필수입니다");
        this.hmacSecret = hmacSecret.getBytes(StandardCharsets.UTF_8);
        this.aesKey = new SecretKeySpec(digest(aesSecret), "AES");
        this.keyVersion = keyVersion;
    }

    public String encryptIp(String ip) {
        if (!usable(ip)) return null;
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(ip.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return keyVersion + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) { throw new IllegalStateException("감사 IP 암호화 실패", e); }
    }

    public String hmacSha256(String value) {
        if (!usable(value)) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("감사 식별자 해시 실패", e); }
    }

    public String toIpPrefix(String ip) {
        if (!usable(ip)) return null;
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length == 4)
                return (bytes[0]&255)+"."+(bytes[1]&255)+"."+(bytes[2]&255)+".0/24";
            for (int i=8; i<bytes.length; i++) bytes[i]=0;
            return InetAddress.getByAddress(bytes).getHostAddress()+"/64";
        } catch (Exception e) { return null; }
    }

    private boolean usable(String value) { return StringUtils.hasText(value) && !"SYSTEM".equals(value); }
    private byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
