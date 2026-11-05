package me.luucka.mangashelf.purchases;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "purchase_lists")
public class PurchaseList {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  private UUID userId;
  private String description;
  private int month;
  private int year;
  private boolean closed;
  private Integer discountCents;
  private int pointsDiscountCents;
  private Instant createdAt;
  private Instant updatedAt;

  protected PurchaseList() {}

  public PurchaseList(UUID userId, String description, int month, int year, Instant now) {
    this.userId = userId;
    this.description = description;
    this.month = month;
    this.year = year;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getDescription() {
    return description;
  }

  public int getMonth() {
    return month;
  }

  public int getYear() {
    return year;
  }

  public boolean isClosed() {
    return closed;
  }

  /** Null means the automatic discount applies. */
  public Integer getDiscountCents() {
    return discountCents;
  }

  public long getPointsDiscountCents() {
    return pointsDiscountCents;
  }

  public void update(
      String description,
      Integer month,
      Integer year,
      Long fixedDiscount,
      boolean auto,
      Long points,
      Instant now) {
    if (description != null) this.description = description;
    if (month != null) this.month = month;
    if (year != null) this.year = year;
    if (auto) this.discountCents = null;
    else if (fixedDiscount != null) this.discountCents = fixedDiscount.intValue();
    if (points != null) this.pointsDiscountCents = points.intValue();
    this.updatedAt = now;
  }
}
