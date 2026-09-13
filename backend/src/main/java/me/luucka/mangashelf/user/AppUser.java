package me.luucka.mangashelf.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.luucka.mangashelf.common.BaseEntity;

import java.time.Instant;

/**
 * A registered account.
 *
 * <p>Named {@code AppUser} because {@code user} is a reserved word in
 * PostgreSQL: an unquoted {@code User} entity would generate queries that
 * fail at runtime.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash; the 72-char column matches BCrypt's fixed output width. */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private UiLanguage language = UiLanguage.IT;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = true;

    @Column(name = "verification_hash", length = 64)
    private String verificationHash;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    @Column(name = "reset_hash", length = 64)
    private String resetHash;

    @Column(name = "reset_expires_at")
    private Instant resetExpiresAt;

    @Column(name = "reset_session_version")
    private Integer resetSessionVersion;

    @Column(name = "deletion_hash", length = 64)
    private String deletionHash;

    @Column(name = "deletion_expires_at")
    private Instant deletionExpiresAt;

    @Column(name = "deletion_session_version")
    private Integer deletionSessionVersion;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "two_factor_secret", length = 256)
    private String twoFactorSecret;

    @Column(name = "two_factor_last_step", nullable = false)
    private long twoFactorLastStep = -1;

    @Column(name = "two_factor_pending_secret", length = 256)
    private String twoFactorPendingSecret;

    @Column(name = "two_factor_pending_expires_at")
    private Instant twoFactorPendingExpiresAt;

    @Column(name = "two_factor_pending_version")
    private Integer twoFactorPendingVersion;

    /**
     * Incremented whenever credentials or authorisation change. Sessions
     * carry the value they authenticated with and are rejected as soon as it
     * no longer matches, so password changes, demotions and disables take
     * effect on every device without keeping a server-side session registry.
     */
    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AppUser(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
