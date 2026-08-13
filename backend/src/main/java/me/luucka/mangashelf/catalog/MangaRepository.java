package me.luucka.mangashelf.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MangaRepository extends JpaRepository<Manga, Long> {

    /** Used by the import job to avoid inserting a work twice. */
    Optional<Manga> findByAnilistId(Integer anilistId);

    Optional<Manga> findByMalId(Integer malId);

    /**
     * Searches across all three title variants, since a user may type the
     * English, romaji or native form interchangeably.
     */
    @Query("""
            SELECT m FROM Manga m
            WHERE LOWER(m.titleRomaji)  LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(m.titleEnglish) LIKE LOWER(CONCAT('%', :q, '%'))
               OR m.titleNative         LIKE CONCAT('%', :q, '%')
            """)
    Page<Manga> search(@Param("q") String q, Pageable pageable);
}
