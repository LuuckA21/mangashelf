package me.luucka.mangashelf;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.mockito.Mockito.when;

/** Real MIME construction with a mocked transport: tests never connect to SMTP. */
public final class MailTestSupport {
    private MailTestSupport() {}

    public static void prepare(JavaMailSender sender) {
        when(sender.createMimeMessage()).thenAnswer(invocation ->
                new MimeMessage(Session.getInstance(new Properties())));
    }

    public static String body(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = body(multipart.getBodyPart(i), mimeType);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static MimeMessage delivered(MimeMessage message) throws Exception {
        // Serialize and parse to check the same encodings a receiving client sees.
        var output = new java.io.ByteArrayOutputStream();
        message.writeTo(output);
        return new MimeMessage(Session.getInstance(new Properties()),
                new java.io.ByteArrayInputStream(output.toByteArray()));
    }
}
