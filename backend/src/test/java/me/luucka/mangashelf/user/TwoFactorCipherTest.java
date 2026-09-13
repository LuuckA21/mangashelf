package me.luucka.mangashelf.user;

import me.luucka.mangashelf.common.ApiException;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class TwoFactorCipherTest {
    @Test void ciphertextIsRandomAuthenticatedAndBoundToItsAccount() {
        var cipher = new TwoFactorCipher(Base64.getEncoder().encodeToString(new byte[32]));
        byte[] secret = Totp.newSecret();
        String encrypted = cipher.encrypt(1, secret);
        assertThat(cipher.encrypt(1, secret)).isNotEqualTo(encrypted);
        assertThat(cipher.decrypt(1, encrypted)).isEqualTo(secret);
        assertThatThrownBy(() -> cipher.decrypt(2, encrypted)).isInstanceOf(ApiException.class);
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(3));
        bytes[20] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(1, "v1." + Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(ApiException.class);
        byte[] otherKey = new byte[32]; otherKey[0] = 1;
        var other = new TwoFactorCipher(Base64.getEncoder().encodeToString(otherKey));
        assertThatThrownBy(() -> other.decrypt(1, encrypted)).isInstanceOf(ApiException.class);
    }

    @Test void absentAndMalformedKeysNeverProduceAWorkingFallback() {
        var absent = new TwoFactorCipher("");
        assertThat(absent.available()).isFalse();
        assertThatThrownBy(() -> absent.encrypt(1, Totp.newSecret())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new TwoFactorCipher("invalid")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TwoFactorCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
