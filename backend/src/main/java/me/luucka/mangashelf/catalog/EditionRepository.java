package me.luucka.mangashelf.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EditionRepository extends JpaRepository<Edition, UUID> {

  @Query("select e from Edition e where e.series.id = :seriesId order by lower(e.name)")
  List<Edition> findBySeries(UUID seriesId);

  long countBySeriesId(UUID seriesId);
}
