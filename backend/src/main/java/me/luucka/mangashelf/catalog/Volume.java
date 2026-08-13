package me.luucka.mangashelf.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.luucka.mangashelf.common.BaseEntity;

import java.time.LocalDate;

/**
 * A single tome within a {@link Series}: the thing that carries an ISBN and
 * a release date, and the thing a user actually owns a copy of.
 */
@Entity
@Table(
        name = "volume",
        uniqueConstraints = @UniqueConstraint(columnNames = {"series_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
public class Volume extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    @Column(nullable = false)
    private Short number;

    @Column(length = 300)
    private String title;

    @Column(length = 13)
    private String isbn13;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    public Volume(Series series, Short number) {
        this.series = series;
        this.number = number;
    }

    /** True when the release date is set and still in the future. */
    public boolean isUpcoming() {
        return releaseDate != null && releaseDate.isAfter(LocalDate.now());
    }
}
