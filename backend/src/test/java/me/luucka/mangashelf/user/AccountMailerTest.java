package me.luucka.mangashelf.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountMailerTest {
    @Test
    void validatesEnabledConfiguration() {
        JavaMailSender sender = mock(JavaMailSender.class);
        for (String url : new String[]{"", "http://example.test", "https://user@example.test",
                "https://example.test?url=evil", "https://example.test/#fragment", "https://example.test/subpath"}) {
            assertThatThrownBy(() -> new AccountMailer(sender, true, url, "sender@example.test", "smtp.example.test"))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(new AccountMailer(sender, false, "", "", "").enabled()).isFalse();
    }

    @Test
    void composesItalianAndEnglishMessagesUsingOnlyConfiguredOrigin() {
        JavaMailSender sender = mock(JavaMailSender.class);
        AccountMailer mailer = new AccountMailer(sender, true, "https://manga.example.test/", "sender@example.test", "smtp.example.test");
        AppUser user = new AppUser("reader", "reader@example.test", "hash");
        mailer.send(user, "token", true);
        user.setLanguage(UiLanguage.EN);
        mailer.send(user, "token", false);
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(2)).send(messages.capture());
        assertThat(messages.getAllValues().get(0).getSubject()).contains("Conferma");
        assertThat(messages.getAllValues().get(0).getText()).contains("24 ore", "https://manga.example.test/verify-email#token=token");
        assertThat(messages.getValue().getText()).contains("30 minutes", "https://manga.example.test/reset-password#token=token");
        assertThat(messages.getValue().getTo()).containsExactly("reader@example.test");
    }
}
