package me.luucka.mangashelf.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import jakarta.mail.internet.MimeMessage;
import me.luucka.mangashelf.MailTestSupport;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountMailerTest {
    @Test
    void deletionEmailExplainsConsequencesAndUsesADistinctLink() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailTestSupport.prepare(sender);
        AccountMailer mailer = new AccountMailer(sender, true, "https://manga.example.test", "sender@example.test", "smtp.example.test");
        AppUser account = new AppUser("reader", "reader@example.test", "hash");
        mailer.sendDeletion(account, "token");
        account.setLanguage(UiLanguage.EN);
        mailer.sendDeletion(account, "token");
        var capture = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(2)).send(capture.capture());
        var italian = MailTestSupport.delivered(capture.getAllValues().get(0));
        var english = MailTestSupport.delivered(capture.getValue());
        assertThat(MailTestSupport.body(italian, "text/plain")).contains("reader@example.test", "eliminati definitivamente", "30 minuti", "/delete-account#token=token");
        assertThat(MailTestSupport.body(english, "text/plain")).contains("permanently deleted", "30 minutes");
        assertThat(MailTestSupport.body(english, "text/html")).contains("Review account deletion", "/delete-account#token=token").doesNotContain("{{");
    }

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
    void composesItalianAndEnglishMessagesUsingOnlyConfiguredOrigin() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailTestSupport.prepare(sender);
        AccountMailer mailer = new AccountMailer(sender, true, "https://manga.example.test/", "sender@example.test", "smtp.example.test");
        AppUser user = new AppUser("reader", "reader@example.test", "hash");
        mailer.send(user, "token", true);
        user.setLanguage(UiLanguage.EN);
        mailer.send(user, "token", false);
        var messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(2)).send(messages.capture());
        MimeMessage confirmation = MailTestSupport.delivered(messages.getAllValues().get(0));
        MimeMessage reset = MailTestSupport.delivered(messages.getValue());
        assertThat(confirmation.getSubject()).contains("Conferma");
        assertThat(MailTestSupport.body(confirmation, "text/plain")).contains("24 ore", "https://manga.example.test/verify-email#token=token");
        assertThat(MailTestSupport.body(reset, "text/plain")).contains("30 minutes", "https://manga.example.test/reset-password#token=token");
        assertThat(reset.getAllRecipients()[0].toString()).isEqualTo("reader@example.test");
        assertThat(reset.getFrom()[0].toString()).contains("MangaShelf", "sender@example.test");
        for (MimeMessage message : new MimeMessage[]{confirmation, reset}) {
            String html = MailTestSupport.body(message, "text/html");
            assertThat(html).contains("MangaShelf", "href=\"https://manga.example.test/")
                    .doesNotContain("{{", "<script", "<img");
        }
        assertThat(MailTestSupport.body(confirmation, "text/html")).contains("lang=\"it\"", "Conferma email", "24 ore");
        assertThat(MailTestSupport.body(reset, "text/html")).contains("lang=\"en\"", "Reset password", "30 minutes");
    }

    @Test
    void escapesValuesInHtmlWithoutChangingThePlainTextLink() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailTestSupport.prepare(sender);
        AccountMailer mailer = new AccountMailer(sender, true, "https://manga.example.test", "sender@example.test", "smtp.example.test");
        mailer.send(new AppUser("reader", "reader@example.test", "hash"), "a\"<b>&", true);
        var capture = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(capture.capture());
        MimeMessage delivered = MailTestSupport.delivered(capture.getValue());
        assertThat(MailTestSupport.body(delivered, "text/html")).contains("a&quot;&lt;b&gt;&amp;").doesNotContain("<b>");
        assertThat(MailTestSupport.body(delivered, "text/plain")).contains("a\"<b>&");
    }
}
