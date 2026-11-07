package me.luucka.mangashelf.publishers;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "publishers")
public class Publisher {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  private String name;
  private Instant createdAt;
  private Instant updatedAt;

  protected Publisher() {}

  public Publisher(String name, Instant now) {
    this.name = name;
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public void rename(String name) {
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
