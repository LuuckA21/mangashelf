package me.luucka.mangashelf.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.luucka.mangashelf.catalog.Series;
import me.luucka.mangashelf.common.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One line of a purchase list.
 *
 * <p>Points at the edition and keeps the number as a plain value, the same
 * shape ownership uses: nothing anywhere records that volume 47 exists, so
 * a list can name a volume months before it is out.
 */
@Entity
@Table(name = "purchase_item")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private PurchaseList list;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    @Column(name = "volume_number", nullable = false)
    private Short volumeNumber;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    /** Cover price in euro cents. */
    @Column(name = "price_eur_cents")
    private Integer priceEurCents;

    /** Shop price in franc cents — the one actually paid. */
    @Column(name = "price_chf_cents")
    private Integer priceChfCents;

    /** Set aside at the shop, waiting to be collected. */
    @Column(nullable = false)
    private boolean reserved = false;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    public PurchaseItem(Series series, Short volumeNumber) {
        this.series = series;
        this.volumeNumber = volumeNumber;
    }
}
