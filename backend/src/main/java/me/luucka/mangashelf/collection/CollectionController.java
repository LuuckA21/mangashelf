package me.luucka.mangashelf.collection;

import jakarta.validation.Valid;
import java.util.UUID;
import me.luucka.mangashelf.catalog.EditionStatus;
import me.luucka.mangashelf.catalog.EditionType;
import me.luucka.mangashelf.collection.dto.CollectionEntry;
import me.luucka.mangashelf.collection.dto.OwnedVolumesRequest;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CollectionController {

  private final CollectionService service;

  public CollectionController(CollectionService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/collection")
  public Page<CollectionEntry> list(
      @AuthenticationPrincipal AppUserPrincipal me,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) EditionType type,
      @RequestParam(required = false) EditionStatus status,
      @RequestParam(defaultValue = "false") boolean incomplete,
      Pageable pageable) {
    return service.list(me.id(), q, type, status, incomplete, pageable);
  }

  @PutMapping("/api/v1/collection/editions/{editionId}")
  public CollectionEntry add(
      @AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID editionId) {
    return service.add(me.id(), editionId);
  }

  @DeleteMapping("/api/v1/collection/editions/{editionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID editionId) {
    service.remove(me.id(), editionId);
  }

  @PutMapping("/api/v1/collection/editions/{editionId}/volumes")
  public CollectionEntry setOwned(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID editionId,
      @Valid @RequestBody OwnedVolumesRequest body) {
    return service.setOwned(me.id(), editionId, body.owned());
  }
}
