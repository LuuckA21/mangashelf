package me.luucka.mangashelf.user;

import me.luucka.mangashelf.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM with independent key, random nonce and account-bound associated data. */
@Component
public class TwoFactorCipher {
    private final SecretKeySpec key;

    public TwoFactorCipher(@Value("${app.security.two-factor-key:}") String encoded) {
        if (encoded.isBlank()) {
            key = null;
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length != 32) throw new IllegalArgumentException();
            key = new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_2FA_ENCRYPTION_KEY must encode exactly 32 bytes in Base64");
        }
    }

    public boolean available() { return key != null; }

    public String encrypt(long userId, byte[] secret) {
        requireAvailable();
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        try {
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, userId, nonce).doFinal(secret);
            return "v1." + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException ex) {
            throw unavailable();
        }
    }

    public byte[] decrypt(long userId, String encrypted) {
        requireAvailable();
        try {
            if (!encrypted.startsWith("v1.")) throw new IllegalArgumentException();
            ByteBuffer value = ByteBuffer.wrap(Base64.getDecoder().decode(encrypted.substring(3)));
            if (value.remaining() != 48) throw new IllegalArgumentException(); // 12 nonce + 20 secret + 16 tag
            byte[] nonce = new byte[12];
            value.get(nonce);
            byte[] body = new byte[value.remaining()];
            value.get(body);
            return cipher(Cipher.DECRYPT_MODE, userId, nonce).doFinal(body);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // Fail closed; no secret, ciphertext or key appears in an error/log.
            throw unavailable();
        }
    }

    private Cipher cipher(int mode, long userId, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("mangashelf:totp:v1:" + userId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    public void requireAvailable() { if (key == null) throw unavailable(); }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "two_factor_unavailable");
    }
}
