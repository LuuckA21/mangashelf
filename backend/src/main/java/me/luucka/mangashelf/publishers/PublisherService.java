package me.luucka.mangashelf.publishers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.luucka.mangashelf.catalog.SeriesService;
import me.luucka.mangashelf.common.ConflictException;
import me.luucka.mangashelf.common.FieldValidationException;
import me.luucka.mangashelf.common.NotFoundException;
import me.luucka.mangashelf.publishers.dto.PublisherResponse;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublisherService {

  private final PublisherRepository publishers;

  public PublisherService(PublisherRepository publishers) {
    this.publishers = publishers;
  }

  @Transactional(readOnly = true)
  public List<PublisherResponse> list() {
    return publishers.findAllSorted().stream().map(PublisherService::toResponse).toList();
  }

  public Publisher require(UUID id) {
    return publishers.findById(id).orElseThrow(() -> new NotFoundException("Editore non trovato"));
  }

  /** A concurrent duplicate falls through to uq_publishers_name (409). */
  @Transactional
  public PublisherResponse create(String name, AppUserPrincipal me) {
    SeriesService.requireAdmin(me);
    var n = name.strip();
    if (publishers.existsWithName(n)) {
      throw new FieldValidationException("name", "Editore già presente");
    }
    return toResponse(publishers.saveAndFlush(new Publisher(n, Instant.now())));
  }

  @Transactional
  public PublisherResponse rename(UUID id, String name, AppUserPrincipal me) {
    SeriesService.requireAdmin(me);
    var p = require(id);
    var n = name.strip();
    if (publishers.existsOtherWithName(n, id)) {
      throw new FieldValidationException("name", "Editore già presente");
    }
    p.rename(n);
    return toResponse(publishers.saveAndFlush(p));
  }

  @Transactional
  public void delete(UUID id, AppUserPrincipal me) {
    SeriesService.requireAdmin(me);
    var p = require(id);
    if (publishers.isUsed(id)) {
      throw new ConflictException("L'editore è usato da alcune edizioni");
    }
    publishers.delete(p);
  }

  private static PublisherResponse toResponse(Publisher p) {
    return new PublisherResponse(p.getId().toString(), p.getName());
  }
}
