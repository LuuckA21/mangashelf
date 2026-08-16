package me.luucka.mangashelf.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeriesRepository extends JpaRepository<Series, Long> {

    Optional<Series> findByMangaIdAndPublisherIgnoreCaseAndLanguageAndNameIgnoreCase(
            Long mangaId, String publisher, String language, String name);

    /**
     * The work is fetched because every response names it, and the DTO is
     * built once the transaction has closed.
     */
    @EntityGraph(attributePaths = "manga")
    Optional<Series> findWithMangaById(Long id);

    /** Same reasoning, for the list of editions under one work. */
    @EntityGraph(attributePaths = "manga")
    List<Series> findByMangaId(Long mangaId);
}
