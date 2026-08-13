package me.luucka.mangashelf.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.luucka.mangashelf.common.BaseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One published run of a {@link Manga} by one publisher, in one language.
 *
 * <p>This is the level external metadata APIs do not model: AniList knows
 * <em>Berserk</em>, not that Star Comics printed it both as a 40-volume run
 * and as the oversized Maximum edition with a different numbering. Volumes
 * therefore hang off the series, never off the manga.
 */
@Entity
@Table(
        name = "series",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"manga_id", "publisher", "language", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Series extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manga_id", nullable = false)
    private Manga manga;

    /** Star Comics, Planet Manga, J-POP, Shueisha... */
    @Column(nullable = false, length = 200)
    private String publisher;

    /**
     * ISO 639-1 code; {@code it} for the Italian editions.
     *
     * <p>Deliberately varchar and not char: PostgreSQL pads a {@code char}
     * column with trailing spaces, so a two-letter code read back from a
     * wider column would no longer equal the constant it was written from.
     */
    @Column(nullable = false, length = 2)
    private String language = "it";

    /** Name of the run: "New Edition", "Deluxe", "Gold", "tankobon". */
    @Column(nullable = false, length = 300)
    private String name;

    /** Announced volume count; null while the run is ongoing and open-ended. */
    @Column(name = "total_volumes")
    private Short totalVolumes;

    /** True once the run has finished publishing. */
    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Volume> volumes = new ArrayList<>();

    public Series(Manga manga, String publisher, String name) {
        this.manga = manga;
        this.publisher = publisher;
        this.name = name;
    }

    public void addVolume(Volume v) {
        volumes.add(v);
        v.setSeries(this);
    }

    public void removeVolume(Volume v) {
        volumes.remove(v);
        v.setSeries(null);
    }
}
