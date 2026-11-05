package me.luucka.mangashelf.users;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
public class AppUser {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  private String username;
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  private Role role;

  private boolean mustChangePassword;
  private BigDecimal chfRate = new BigDecimal("1.20");
  private int discountPercent = 10;
  private Instant createdAt;
  private Instant updatedAt;

  protected AppUser() {}

  public AppUser(
      String username, String passwordHash, Role role, boolean mustChangePassword, Instant now) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
    this.mustChangePassword = mustChangePassword;
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public void changePassword(String passwordHash) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = false;
  }

  public void resetPassword(String passwordHash) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = true;
  }

  public void changeRole(Role role) {
    this.role = role;
  }

  public void setPurchaseSettings(BigDecimal chfRate, int discountPercent) {
    this.chfRate = chfRate;
    this.discountPercent = discountPercent;
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public BigDecimal getChfRate() {
    return chfRate;
  }

  public int getDiscountPercent() {
    return discountPercent;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
