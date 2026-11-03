package me.luucka.mangashelf.collection;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "owned_volumes")
public class OwnedVolume {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  private UUID collectionEditionId;
  private int number;
  private Instant createdAt;
  private Instant updatedAt;

  protected OwnedVolume() {}

  public OwnedVolume(UUID collectionEditionId, int number, Instant now) {
    this.collectionEditionId = collectionEditionId;
    this.number = number;
    this.createdAt = now;
    this.updatedAt = now;
  }
}
