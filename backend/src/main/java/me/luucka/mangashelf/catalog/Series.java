package me.luucka.mangashelf.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "series")
public class Series {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  private String title;
  private UUID createdBy;
  private Instant createdAt;
  private Instant updatedAt;

  protected Series() {}

  public Series(String title, UUID createdBy, Instant now) {
    this.title = title;
    this.createdBy = createdBy;
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public void rename(String title) {
    this.title = title;
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}
