package me.luucka.mangashelf.catalog;

import java.util.UUID;
import me.luucka.mangashelf.catalog.dto.SeriesSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeriesRepository extends JpaRepository<Series, UUID> {

  /**
   * Blank {@code q} matches everything; wildcards in {@code q} must be escaped with LikeEscape; the
   * sort is fixed, so pass an unsorted Pageable.
   */
  @Query(
      value =
          """
          select new me.luucka.mangashelf.catalog.dto.SeriesSummary(
            cast(s.id as string), s.title,
            (select count(e) from Edition e where e.series = s))
          from Series s
          where :q = '' or lower(s.title) like concat('%', lower(:q), '%') escape '\\'
          order by lower(s.title)
          """,
      countQuery =
          "select count(s) from Series s where :q = '' or lower(s.title) like concat('%', lower(:q), '%')")
  Page<SeriesSummary> search(@Param("q") String q, Pageable pageable);
}
