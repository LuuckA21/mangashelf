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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AppUser(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
