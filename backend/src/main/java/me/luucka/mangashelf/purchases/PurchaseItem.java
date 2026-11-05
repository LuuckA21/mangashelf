package me.luucka.mangashelf.purchases;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "purchase_items")
public class PurchaseItem {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  private UUID listId;
  private UUID editionId;
  private int volume;
  private LocalDate releaseDate;
  private int priceEurCents;
  private int priceChfCents;
  private boolean booked;
  private Instant createdAt;
  private Instant updatedAt;

  protected PurchaseItem() {}

  public PurchaseItem(
      UUID listId,
      UUID editionId,
      int volume,
      LocalDate releaseDate,
      long priceEurCents,
      long priceChfCents,
      boolean booked,
      Instant now) {
    this.listId = listId;
    this.editionId = editionId;
    this.volume = volume;
    this.releaseDate = releaseDate;
    this.priceEurCents = (int) priceEurCents;
    this.priceChfCents = (int) priceChfCents;
    this.booked = booked;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getListId() {
    return listId;
  }

  public void update(
      UUID editionId,
      Integer volume,
      LocalDate releaseDate,
      boolean clearDate,
      Long eur,
      Long chf,
      Boolean booked,
      Instant now) {
    if (editionId != null) this.editionId = editionId;
    if (volume != null) this.volume = volume;
    if (clearDate) this.releaseDate = null;
    else if (releaseDate != null) this.releaseDate = releaseDate;
    if (eur != null) this.priceEurCents = eur.intValue();
    if (chf != null) this.priceChfCents = chf.intValue();
    if (booked != null) this.booked = booked;
    this.updatedAt = now;
  }
}
