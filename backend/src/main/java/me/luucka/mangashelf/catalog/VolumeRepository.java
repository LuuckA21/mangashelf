package me.luucka.mangashelf.catalog;

import me.luucka.mangashelf.catalog.dto.SeriesVolumeNumber;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VolumeRepository extends JpaRepository<Volume, Long> {

    /** The series is fetched because the DTO is built outside the session. */
    @EntityGraph(attributePaths = "series")
    List<Volume> findBySeriesIdOrderByNumberAsc(Long seriesId);

    Optional<Volume> findBySeriesIdAndNumber(Long seriesId, Short number);

    /**
     * Every volume number of the given editions, in one query.
     *
     * <p>The alternative — asking each edition in turn — would issue one
     * round trip per row of the collection page.
     */
    @Query("""
            SELECT new me.luucka.mangashelf.catalog.dto.SeriesVolumeNumber(
                       v.series.id, v.number)
            FROM Volume v
            WHERE v.series.id IN :seriesIds
            ORDER BY v.number ASC
            """)
    List<SeriesVolumeNumber> findNumbersBySeriesIds(@Param("seriesIds") List<Long> seriesIds);
}
