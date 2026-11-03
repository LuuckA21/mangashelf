package me.luucka.mangashelf.catalog;

import java.time.Instant;
import java.util.UUID;
import me.luucka.mangashelf.catalog.dto.EditionRequest;
import me.luucka.mangashelf.catalog.dto.EditionResponse;
import me.luucka.mangashelf.collection.CollectionEditionRepository;
import me.luucka.mangashelf.collection.OwnedVolumeRepository;
import me.luucka.mangashelf.common.ConflictException;
import me.luucka.mangashelf.common.FieldValidationException;
import me.luucka.mangashelf.common.NotFoundException;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditionService {

  private final EditionRepository editions;
  private final SeriesRepository series;
  private final CollectionEditionRepository collection;
  private final OwnedVolumeRepository ownedVolumes;

  public EditionService(
      EditionRepository editions,
      SeriesRepository series,
      CollectionEditionRepository collection,
      OwnedVolumeRepository ownedVolumes) {
    this.editions = editions;
    this.series = series;
    this.collection = collection;
    this.ownedVolumes = ownedVolumes;
  }

  @Transactional(readOnly = true)
  public EditionResponse get(UUID id, AppUserPrincipal me) {
    return toResponse(require(id), me);
  }

  public Edition require(UUID id) {
    return editions.findById(id).orElseThrow(() -> new NotFoundException("Edizione non trovata"));
  }

  /** A duplicate name in the same series surfaces as a unique-index violation (409). */
  @Transactional
  public EditionResponse create(UUID seriesId, EditionRequest r, AppUserPrincipal me) {
    var s = series.findById(seriesId).orElseThrow(() -> new NotFoundException("Serie non trovata"));
    var e =
        new Edition(
            s,
            r.name().strip(),
            publisher(r),
            r.type(),
            r.status(),
            r.volumeCount(),
            me.id(),
            Instant.now());
    return toResponse(editions.saveAndFlush(e), me);
  }

  @Transactional
  public EditionResponse update(UUID id, EditionRequest r, AppUserPrincipal me) {
    SeriesService.requireAdmin(me);
    var e = require(id);
    // @Version alone cannot detect a stale client version on a freshly loaded entity.
    if (r.version() == null) {
      throw new FieldValidationException("version", "Obbligatorio");
    }
    if (r.version() != e.getVersion()) {
      throw new ConflictException("L'edizione è stata modificata da un altro utente");
    }
    if (ownedVolumes.findMaxNumberByEditionId(id).orElse(0) > r.volumeCount()) {
      throw new ConflictException("Alcuni utenti possiedono volumi oltre questo numero");
    }
    e.update(r.name().strip(), publisher(r), r.type(), r.status(), r.volumeCount());
    return toResponse(editions.saveAndFlush(e), me);
  }

  @Transactional
  public void delete(UUID id, AppUserPrincipal me) {
    SeriesService.requireAdmin(me);
    editions.delete(require(id));
  }

  /** Call inside a transaction: reads the lazy series. */
  EditionResponse toResponse(Edition e, AppUserPrincipal me) {
    return toResponse(e, me, collection.existsByUserIdAndEditionId(me.id(), e.getId()));
  }

  EditionResponse toResponse(Edition e, AppUserPrincipal me, boolean inMyCollection) {
    return new EditionResponse(
        e.getId().toString(),
        e.getSeries().getId().toString(),
        e.getSeries().getTitle(),
        e.getName(),
        e.getPublisher(),
        e.getType(),
        e.getStatus(),
        e.getVolumeCount(),
        e.getCoverFile() != null,
        me.isAdmin() || me.id().equals(e.getCreatedBy()),
        e.getVersion(),
        collection.countByEditionId(e.getId()),
        inMyCollection);
  }

  private static String publisher(EditionRequest r) {
    var p = r.publisher() == null ? "" : r.publisher().strip();
    return p.isEmpty() ? null : p;
  }
}
