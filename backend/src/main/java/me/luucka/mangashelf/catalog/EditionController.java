package me.luucka.mangashelf.catalog;

import jakarta.validation.Valid;
import java.util.UUID;
import me.luucka.mangashelf.catalog.dto.EditionRequest;
import me.luucka.mangashelf.catalog.dto.EditionResponse;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class EditionController {

  private final EditionService service;

  public EditionController(EditionService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/editions/{id}")
  public EditionResponse get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    return service.get(id, me);
  }

  @PostMapping("/api/v1/series/{seriesId}/editions")
  public ResponseEntity<EditionResponse> create(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID seriesId,
      @Valid @RequestBody EditionRequest body) {
    var created = service.create(seriesId, body, me);
    var location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/editions/{id}")
            .build(created.id());
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/api/v1/editions/{id}")
  public EditionResponse update(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @Valid @RequestBody EditionRequest body) {
    return service.update(id, body, me);
  }

  @DeleteMapping("/api/v1/editions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    service.delete(id, me);
  }
}
