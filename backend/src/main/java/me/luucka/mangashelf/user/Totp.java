package me.luucka.mangashelf.user;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/** RFC 6238, HMAC-SHA1, six digits, 30-second period. No custom crypto primitive. */
public final class Totp {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private Totp() {}

    public static byte[] newSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(BASE32[(buffer >>> bits) & 31]);
            }
        }
        if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 31]);
        return out.toString();
    }

    static String code(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 15;
            int binary = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            int modulus = digits == 8 ? 100_000_000 : 1_000_000;
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % modulus);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP unavailable", ex);
        }
    }

    /** Returns the accepted step; callers persist it under a row lock to prevent replay. */
    public static long match(byte[] secret, String supplied, long epochSeconds, long lastStep) {
        if (supplied == null || !supplied.matches("[0-9]{6}")) return -1;
        long now = epochSeconds / 30;
        long matched = -1;
        for (long step = now - 1; step <= now + 1; step++) {
            boolean equal = MessageDigest.isEqual(code(secret, step, 6).getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII));
            if (equal && step > lastStep) matched = step;
        }
        return matched;
    }
}
