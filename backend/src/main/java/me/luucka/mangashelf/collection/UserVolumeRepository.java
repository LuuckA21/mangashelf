package me.luucka.mangashelf.collection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserVolumeRepository extends JpaRepository<UserVolume, UserVolumeId> {

    boolean existsByIdUserIdAndIdSeriesIdAndIdNumber(Long userId, Long seriesId, Short number);

    /** The numbers owned of one edition, in reading order. */
    @Query("""
            SELECT uv.id.number FROM UserVolume uv
            WHERE uv.id.userId = :userId AND uv.id.seriesId = :seriesId
            ORDER BY uv.id.number ASC
            """)
    List<Short> findNumbers(@Param("userId") Long userId,
                            @Param("seriesId") Long seriesId);

    /**
     * Everything a user owns, newest first.
     *
     * <p>The edition and its work are fetched along: the response names both,
     * and left lazy each row would cost two more queries.
     */
    @EntityGraph(attributePaths = {"series", "series.manga"})
    List<UserVolume> findByIdUserIdOrderByAddedAtDesc(Long userId);

    /**
     * How many users own volumes of this edition. Guards deletion of a
     * shared catalogue row, whose cascade would reach other people's shelves.
     */
    long countByIdSeriesId(Long seriesId);

    /** Same guard, widened to every edition of a work. */
    @Query("SELECT COUNT(uv) FROM UserVolume uv WHERE uv.series.manga.id = :mangaId")
    long countByMangaId(@Param("mangaId") Long mangaId);

    void deleteByIdUserIdAndIdSeriesIdAndIdNumber(Long userId, Long seriesId, Short number);
}
