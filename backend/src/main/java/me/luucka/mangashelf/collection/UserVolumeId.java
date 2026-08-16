package me.luucka.mangashelf.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite key of {@link UserVolume}: user, edition and volume number.
 *
 * <p>No surrogate id, because the triple is already the identity — a user
 * either owns volume 47 of a run or does not.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class UserVolumeId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "number")
    private Short number;

    public UserVolumeId(Long userId, Long seriesId, Short number) {
        this.userId = userId;
        this.seriesId = seriesId;
        this.number = number;
    }
}
