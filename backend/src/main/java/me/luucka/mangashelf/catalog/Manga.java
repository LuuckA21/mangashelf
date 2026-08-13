package me.luucka.mangashelf.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.luucka.mangashelf.common.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The work itself, independent of how any publisher packaged it.
 *
 * <p>Fields here are canonical metadata sourced from AniList or Jikan and
 * refreshed by the import job. Anything specific to a printed run belongs on
 * {@link Series} instead.
 */
@Entity
@Table(name = "manga")
@Getter
@Setter
@NoArgsConstructor
public class Manga extends BaseEntity {

    /** External id on AniList; null for manually created entries. */
    @Column(name = "anilist_id", unique = true)
    private Integer anilistId;

    /** External id on MyAnimeList, via Jikan. */
    @Column(name = "mal_id", unique = true)
    private Integer malId;

    @Column(name = "title_romaji", nullable = false, length = 500)
    private String titleRomaji;

    @Column(name = "title_native", length = 500)
    private String titleNative;

    @Column(name = "title_english", length = 500)
    private String titleEnglish;

    @Column(columnDefinition = "text")
    private String authors;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PublicationStatus status;

    /**
     * Mapped onto a native PostgreSQL {@code text[]} rather than a join
     * table: genres are only ever read as a whole with the manga and never
     * queried independently, so a second table would buy nothing.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] genres;

    @Column(name = "start_year")
    private Short startYear;

    /** Volume count of the original Japanese run, not of any local series. */
    @Column(name = "total_volumes")
    private Short totalVolumes;

    /** Last successful metadata refresh; null means never synced. */
    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "manga", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Series> series = new ArrayList<>();

    public Manga(String titleRomaji) {
        this.titleRomaji = titleRomaji;
    }

    /** Keeps both sides of the association in sync. */
    public void addSeries(Series s) {
        series.add(s);
        s.setManga(this);
    }

    public void removeSeries(Series s) {
        series.remove(s);
        s.setManga(null);
    }

    /** Best available display title, preferring English, then romaji. */
    public String displayTitle() {
        return titleEnglish != null ? titleEnglish : titleRomaji;
    }
}
