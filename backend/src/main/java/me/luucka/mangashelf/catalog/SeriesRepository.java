package me.luucka.mangashelf.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeriesRepository extends JpaRepository<Series, Long> {

    List<Series> findByPublisherIgnoreCase(String publisher);

    Optional<Series> findByMangaIdAndPublisherIgnoreCaseAndLanguageAndNameIgnoreCase(
            Long mangaId, String publisher, String language, String name);

    /**
     * Loads manga and volumes along with the series.
     *
     * <p>Both are needed by {@code SeriesResponse}, which is built in the
     * controller — after the transaction has closed. With
     * {@code open-in-view: false} an uninitialised proxy there throws
     * {@code LazyInitializationException}, so the associations the DTO
     * touches must be fetched here rather than on demand.
     */
    @EntityGraph(attributePaths = {"manga", "volumes"})
    Optional<Series> findWithVolumesById(Long id);

    /**
     * Same reasoning as above, for the list of editions under one work.
     *
     * <p>The method name must remain a valid derived query: everything after
     * "By" is parsed as a property path, so a suffix like "WithVolumes"
     * would fail at startup. The eager loading is expressed by the
     * annotation, not by the name.
     */
    @EntityGraph(attributePaths = {"manga", "volumes"})
    List<Series> findByMangaId(Long mangaId);
}
