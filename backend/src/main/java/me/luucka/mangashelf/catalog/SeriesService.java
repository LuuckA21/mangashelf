package me.luucka.mangashelf.catalog;

import java.time.Instant;
import java.util.UUID;
import me.luucka.mangashelf.catalog.dto.SeriesDetail;
import me.luucka.mangashelf.catalog.dto.SeriesSummary;
import me.luucka.mangashelf.common.ConflictException;
import me.luucka.mangashelf.common.LikeEscape;
import me.luucka.mangashelf.common.NotFoundException;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeriesService {

  private final SeriesRepository series;
  private final EditionRepository editions;
  private final EditionService editionService;

  public SeriesService(
      SeriesRepository series, EditionRepository editions, EditionService editionService) {
    this.series = series;
    this.editions = editions;
    this.editionService = editionService;
  }

  @Transactional(readOnly = true)
  public Page<SeriesSummary> list(String q, Pageable pageable) {
    var unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    return series.search(q == null ? "" : LikeEscape.escape(q.strip()), unsorted);
  }

  @Transactional(readOnly = true)
  public SeriesDetail detail(UUID id, AppUserPrincipal me) {
    var s = require(id);
    return new SeriesDetail(
        s.getId().toString(),
        s.getTitle(),
        editions.findBySeries(id).stream().map(e -> editionService.toResponse(e, me)).toList());
  }

  /** A duplicate title surfaces as a unique-index violation, which the API maps to 409. */
  public Series create(String title, AppUserPrincipal me) {
    return series.saveAndFlush(new Series(title.strip(), me.id(), Instant.now()));
  }

  @Transactional
  public SeriesSummary rename(UUID id, String title, AppUserPrincipal me) {
    requireAdmin(me);
    var s = require(id);
    s.rename(title.strip());
    series.saveAndFlush(s);
    return new SeriesSummary(s.getId().toString(), s.getTitle(), editions.countBySeriesId(id));
  }

  @Transactional
  public void delete(UUID id, AppUserPrincipal me) {
    requireAdmin(me);
    var s = require(id);
    if (editions.countBySeriesId(id) > 0) {
      throw new ConflictException("La serie ha ancora delle edizioni");
    }
    series.delete(s);
  }

  Series require(UUID id) {
    return series.findById(id).orElseThrow(() -> new NotFoundException("Serie non trovata"));
  }

  static void requireAdmin(AppUserPrincipal me) {
    if (!me.isAdmin()) {
      throw new AccessDeniedException("Admin only");
    }
  }
}
