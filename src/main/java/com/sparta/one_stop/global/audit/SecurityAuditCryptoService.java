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
import java.util.regex.Pattern;

@Component
public class SecurityAuditCryptoService {
    private static final int MIN_SECRET_BYTES = 32;
    private static final Pattern NUMERIC_IPV6 = Pattern.compile("[0-9a-fA-F:.]+");
    private final byte[] hmacSecret;
    private final SecretKeySpec aesKey;
    private final String keyVersion;
    private final SecureRandom random = new SecureRandom();

    public SecurityAuditCryptoService(
        @Value("${security.audit.hmac-secret}") String hmacSecret,
        @Value("${security.audit.aes-secret}") String aesSecret,
        @Value("${security.audit.aes-key-version:v1}") String keyVersion
    ) {
        validateSecret("security.audit.hmac-secret", hmacSecret);
        validateSecret("security.audit.aes-secret", aesSecret);
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
            byte[] bytes = parseNumericIp(ip);
            if (bytes == null) return null;
            if (bytes.length == 4)
                return (bytes[0]&255)+"."+(bytes[1]&255)+"."+(bytes[2]&255)+".0/24";
            for (int i=8; i<bytes.length; i++) bytes[i]=0;
            return InetAddress.getByAddress(bytes).getHostAddress()+"/64";
        } catch (Exception e) { return null; }
    }

    private boolean usable(String value) { return StringUtils.hasText(value) && !"SYSTEM".equals(value); }
    private void validateSecret(String propertyName, String secret) {
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(propertyName + " must contain at least 32 UTF-8 bytes");
        }
    }
    private byte[] parseNumericIp(String ip) throws Exception {
        if (ip.indexOf(':') >= 0) {
            if (!NUMERIC_IPV6.matcher(ip).matches()) return null;
            return InetAddress.getByName(ip).getAddress();
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] bytes = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3 || !parts[i].chars().allMatch(Character::isDigit)) return null;
            int value = Integer.parseInt(parts[i]);
            if (value > 255) return null;
            bytes[i] = (byte) value;
        }
        return bytes;
    }
    private byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
