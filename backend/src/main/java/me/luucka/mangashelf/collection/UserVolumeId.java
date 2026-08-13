package me.luucka.mangashelf.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite key of {@link UserVolume}.
 *
 * <p>There is no surrogate id because the pair is already the identity: a
 * user either owns a volume or does not, and there is no third state that
 * a separate key could distinguish.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class UserVolumeId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "volume_id")
    private Long volumeId;

    public UserVolumeId(Long userId, Long volumeId) {
        this.userId = userId;
        this.volumeId = volumeId;
    }
}
