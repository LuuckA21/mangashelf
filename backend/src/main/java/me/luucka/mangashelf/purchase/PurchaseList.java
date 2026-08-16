package me.luucka.mangashelf.purchase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.luucka.mangashelf.common.BaseEntity;
import me.luucka.mangashelf.user.AppUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A month's worth of planned purchases, named by whoever wrote it.
 *
 * <p>Belongs to a user: what you intend to buy is as personal as what you
 * already own.
 */
@Entity
@Table(name = "purchase_list")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseList extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 200)
    private String name;

    /** Percentage off the whole list, or null when the discount is a flat amount. */
    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Flat amount off, in CHF cents, or null when the discount is a percentage. */
    @Column(name = "discount_cents")
    private Integer discountCents;

    /** Year of the month this list covers, or null when it is not tied to one. */
    @Column(name = "period_year")
    private Short periodYear;

    @Column(name = "period_month")
    private Short periodMonth;

    /**
     * When the list was paid, or null while it is still open.
     *
     * <p>A timestamp rather than a flag: "paid" and "paid when" are one
     * fact, and two columns would allow a list that is paid on no date.
     */
    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("releaseDate ASC, id ASC")
    private List<PurchaseItem> items = new ArrayList<>();

    public PurchaseList(AppUser user, String name) {
        this.user = user;
        this.name = name;
    }

    public void addItem(PurchaseItem item) {
        items.add(item);
        item.setList(this);
    }
}
