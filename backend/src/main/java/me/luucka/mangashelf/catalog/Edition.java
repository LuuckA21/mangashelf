package me.luucka.mangashelf.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "editions")
public class Edition {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "series_id")
  private Series series;

  private String name;
  private String publisher;

  @Enumerated(EnumType.STRING)
  private EditionType type;

  @Enumerated(EnumType.STRING)
  private EditionStatus status;

  private int volumeCount;
  private String coverFile;
  private UUID createdBy;

  @Version private long version;

  private Instant createdAt;
  private Instant updatedAt;

  protected Edition() {}

  public Edition(
      Series series,
      String name,
      String publisher,
      EditionType type,
      EditionStatus status,
      int volumeCount,
      UUID createdBy,
      Instant now) {
    this.series = series;
    this.createdBy = createdBy;
    this.createdAt = now;
    this.updatedAt = now;
    update(name, publisher, type, status, volumeCount);
  }

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public void update(
      String name, String publisher, EditionType type, EditionStatus status, int volumeCount) {
    this.name = name;
    this.publisher = publisher;
    this.type = type;
    this.status = status;
    this.volumeCount = volumeCount;
  }

  public UUID getId() {
    return id;
  }

  public Series getSeries() {
    return series;
  }

  public String getName() {
    return name;
  }

  public String getPublisher() {
    return publisher;
  }

  public EditionType getType() {
    return type;
  }

  public EditionStatus getStatus() {
    return status;
  }

  public int getVolumeCount() {
    return volumeCount;
  }

  public String getCoverFile() {
    return coverFile;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public long getVersion() {
    return version;
  }
}
