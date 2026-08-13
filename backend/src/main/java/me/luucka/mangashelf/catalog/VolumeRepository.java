package me.luucka.mangashelf.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VolumeRepository extends JpaRepository<Volume, Long> {

    /** The series is fetched because the DTO is built outside the session. */
    @EntityGraph(attributePaths = "series")
    List<Volume> findBySeriesIdOrderByNumberAsc(Long seriesId);

    Optional<Volume> findBySeriesIdAndNumber(Long seriesId, Short number);

    Optional<Volume> findByIsbn13(String isbn13);
}
