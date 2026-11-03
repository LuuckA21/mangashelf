package me.luucka.mangashelf.collection;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "collection_editions")
public class CollectionEdition {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  private UUID userId;
  private UUID editionId;
  private Instant createdAt;
  private Instant updatedAt;

  protected CollectionEdition() {}

  public CollectionEdition(UUID userId, UUID editionId, Instant now) {
    this.userId = userId;
    this.editionId = editionId;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }
}
