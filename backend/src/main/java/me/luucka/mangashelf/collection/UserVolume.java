package me.luucka.mangashelf.collection;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.luucka.mangashelf.catalog.Series;
import me.luucka.mangashelf.user.AppUser;

import java.time.Instant;

/**
 * A volume number a user owns of an edition.
 *
 * <p>Points at the edition and carries the number as a plain value: there is
 * no row anywhere saying that volume 47 exists, because nobody knows which
 * volumes a publisher has released. Ownership is the only record.
 */
@Entity
@Table(name = "user_volume")
@Getter
@Setter
@NoArgsConstructor
public class UserVolume {

    @EmbeddedId
    private UserVolumeId id;

    /**
     * {@code @MapsId} makes this association reuse the column already
     * declared in the embedded id; without it the same column would be
     * mapped twice and Hibernate would reject the entity at startup.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("seriesId")
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    public UserVolume(AppUser user, Series series, Short number) {
        this.user = user;
        this.series = series;
        this.id = new UserVolumeId(user.getId(), series.getId(), number);
    }

    public Short getNumber() {
        return id.getNumber();
    }
}
