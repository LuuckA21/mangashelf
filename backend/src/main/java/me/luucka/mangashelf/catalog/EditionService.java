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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EditionService {

  private static final Logger log = LoggerFactory.getLogger(EditionService.class);

  private final EditionRepository editions;
  private final SeriesRepository series;
  private final CollectionEditionRepository collection;
  private final OwnedVolumeRepository ownedVolumes;
  private final CoverStorage covers;

  public EditionService(
      EditionRepository editions,
      SeriesRepository series,
      CollectionEditionRepository collection,
      OwnedVolumeRepository ownedVolumes,
      CoverStorage covers) {
    this.editions = editions;
    this.series = series;
    this.collection = collection;
    this.ownedVolumes = ownedVolumes;
    this.covers = covers;
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
    // shortcut: an admin lowering volumeCount while a user PUTs volumes can leave owned numbers
    // above volume_count; a row lock on the edition or a DB trigger is the upgrade if it matters.
    if (ownedVolumes.findMaxNumberByEditionId(id).orElse(0) > r.volumeCount()) {
      throw new ConflictException("Alcuni utenti possiedono volumi oltre questo numero");
    }
    e.update(r.name().strip(), publisher(r), r.type(), r.status(), r.volumeCount());
    return toResponse(editions.saveAndFlush(e), me);
  }

  @Transactional
  public void delete(UUID id, AppUserPrincipal me) {
    SeriesService.requireAdmin(me);
    var e = require(id);
    editions.delete(e);
    afterCompletion(e.getCoverFile(), null);
  }

  @Transactional
  public void putCover(UUID id, byte[] image, AppUserPrincipal me) {
    var e = requireCoverEditor(id, me);
    var name = covers.save(CoverImages.toJpeg(image));
    var old = e.getCoverFile();
    afterCompletion(old, name);
    e.setCoverFile(name);
    editions.saveAndFlush(e);
  }

  @Transactional
  public void deleteCover(UUID id, AppUserPrincipal me) {
    var e = requireCoverEditor(id, me);
    afterCompletion(e.getCoverFile(), null);
    e.setCoverFile(null);
    editions.saveAndFlush(e);
  }

  public byte[] readCover(UUID id) {
    var name = require(id).getCoverFile();
    if (name == null) {
      throw new NotFoundException("Copertina non trovata");
    }
    return covers.read(name);
  }

  private Edition requireCoverEditor(UUID id, AppUserPrincipal me) {
    var e = require(id);
    if (!me.isAdmin() && !me.id().equals(e.getCreatedBy())) {
      throw new AccessDeniedException("Not allowed to change this cover");
    }
    return e;
  }

  /** Deletes {@code onCommit} once the transaction commits, {@code onRollback} if it does not. */
  private void afterCompletion(String onCommit, String onRollback) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            var file = status == STATUS_COMMITTED ? onCommit : onRollback;
            if (file == null) {
              return;
            }
            try {
              covers.delete(file);
            } catch (RuntimeException e) {
              log.warn("Could not delete cover file {}", file, e);
            }
          }
        });
  }

  /** Call inside a transaction: reads the lazy series. */
  EditionResponse toResponse(Edition e, AppUserPrincipal me) {
    return toResponse(
        e,
        me,
        collection.existsByUserIdAndEditionId(me.id(), e.getId()),
        collection.countByEditionId(e.getId()));
  }

  EditionResponse toResponse(
      Edition e, AppUserPrincipal me, boolean inMyCollection, long collectionCount) {
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
        collectionCount,
        inMyCollection);
  }

  private static String publisher(EditionRequest r) {
    var p = r.publisher() == null ? "" : r.publisher().strip();
    return p.isEmpty() ? null : p;
  }
}
