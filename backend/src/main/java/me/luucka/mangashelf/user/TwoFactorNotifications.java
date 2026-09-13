package me.luucka.mangashelf.user;

import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Best-effort alerts after commit; SMTP failure cannot roll back a security change. */
@Component
public class TwoFactorNotifications {
    public enum Action { ENABLED, DISABLED, RECOVERY_REGENERATED }
    public record Change(String email, UiLanguage language, Action action) {
        @Override public String toString() { return "TwoFactorChange[" + action + "]"; }
    }
    private final AccountMailer mailer;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20), new ThreadPoolExecutor.AbortPolicy());

    public TwoFactorNotifications(AccountMailer mailer) { this.mailer = mailer; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(Change change) {
        if (!mailer.enabled()) return;
        try {
            executor.execute(() -> {
                try { mailer.sendSecurityNotice(change.email(), change.language(), change.action()); }
                catch (RuntimeException ex) { failed(); }
            });
        } catch (RejectedExecutionException ex) { failed(); }
    }

    private void failed() {
        LoggerFactory.getLogger(TwoFactorNotifications.class)
                .warn("Could not deliver a 2FA change notification; check SMTP configuration and capacity");
    }
    @PreDestroy public void close() { executor.shutdown(); }
}
