package me.luucka.mangashelf.user;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {
    private final byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test void matchesEveryRfc6238Sha1VectorIncludingBeyond2038() {
        long[] times = {59, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L};
        String[] codes = {"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"};
        for (int i = 0; i < times.length; i++) {
            assertThat(Totp.code(secret, times[i] / 30, 8)).isEqualTo(codes[i]);
            assertThat(Totp.code(secret, times[i] / 30, 6)).isEqualTo(codes[i].substring(2));
        }
        assertThat(Totp.base32(secret)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test void acceptsOnlyAdjacentStepsAndRejectsConsumedCodesAndMalformedInput() {
        long step = 1234567890L / 30;
        for (long value = step - 1; value <= step + 1; value++) {
            String code = Totp.code(secret, value, 6);
            assertThat(Totp.match(secret, code, step * 30, -1)).isEqualTo(value);
            assertThat(Totp.match(secret, code, step * 30, value)).isEqualTo(-1);
        }
        assertThat(Totp.match(secret, Totp.code(secret, step - 2, 6), step * 30, -1)).isEqualTo(-1);
        assertThat(Totp.match(secret, Totp.code(secret, step + 2, 6), step * 30, -1)).isEqualTo(-1);
        assertThat(Totp.match(secret, "１２３４５６", step * 30, -1)).isEqualTo(-1);
        assertThat(Totp.match(secret, "12345", step * 30, -1)).isEqualTo(-1);
    }
}
