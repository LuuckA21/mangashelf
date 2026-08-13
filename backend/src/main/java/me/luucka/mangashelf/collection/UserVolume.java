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
import me.luucka.mangashelf.catalog.Volume;
import me.luucka.mangashelf.user.AppUser;

import java.time.Instant;

/**
 * A volume owned by a user: the join between the shared catalogue and one
 * person's shelf.
 *
 * <p>Carries no attributes of its own beyond the timestamp — the row's
 * existence <em>is</em> the fact being recorded. Condition, price and
 * reading state can be added later without disturbing this shape.
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
    @MapsId("volumeId")
    @JoinColumn(name = "volume_id", nullable = false)
    private Volume volume;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    public UserVolume(AppUser user, Volume volume) {
        this.user = user;
        this.volume = volume;
        this.id = new UserVolumeId(user.getId(), volume.getId());
    }
}
