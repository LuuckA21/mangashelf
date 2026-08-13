package me.luucka.mangashelf.collection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserVolumeRepository extends JpaRepository<UserVolume, UserVolumeId> {

    Optional<UserVolume> findByIdUserIdAndIdVolumeId(Long userId, Long volumeId);

    boolean existsByIdUserIdAndIdVolumeId(Long userId, Long volumeId);

    /**
     * Everything a user owns, newest first.
     *
     * <p>The response walks volume to series to manga, and is assembled in
     * the controller once the session is gone, so the whole chain is fetched
     * up front instead of lazily.
     */
    @EntityGraph(attributePaths = {"volume", "volume.series", "volume.series.manga"})
    List<UserVolume> findByIdUserIdOrderByAddedAtDesc(Long userId);

    /** Owned volumes within one edition, in reading order. */
    @Query("""
            SELECT uv FROM UserVolume uv
            JOIN FETCH uv.volume v
            JOIN FETCH v.series s
            JOIN FETCH s.manga
            WHERE uv.id.userId = :userId AND s.id = :seriesId
            ORDER BY v.number ASC
            """)
    List<UserVolume> findOwnedInSeries(@Param("userId") Long userId,
                                       @Param("seriesId") Long seriesId);

    /** Numbers still missing from an edition — the shopping list. */
    @Query("""
            SELECT v.number FROM Volume v
            WHERE v.series.id = :seriesId
              AND NOT EXISTS (
                  SELECT 1 FROM UserVolume uv
                  WHERE uv.volume = v AND uv.id.userId = :userId)
            ORDER BY v.number ASC
            """)
    List<Short> findMissingNumbers(@Param("userId") Long userId,
                                   @Param("seriesId") Long seriesId);

    /**
     * How many users own this volume. Guards deletion of shared catalogue
     * rows, whose cascade would otherwise reach other people's shelves.
     */
    long countByIdVolumeId(Long volumeId);

    /** Same guard, widened to every volume in an edition. */
    @Query("SELECT COUNT(uv) FROM UserVolume uv WHERE uv.volume.series.id = :seriesId")
    long countOwnedInSeries(@Param("seriesId") Long seriesId);
}
