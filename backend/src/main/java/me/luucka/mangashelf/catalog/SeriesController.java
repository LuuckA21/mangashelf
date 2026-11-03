package me.luucka.mangashelf.catalog;

import jakarta.validation.Valid;
import java.util.UUID;
import me.luucka.mangashelf.catalog.dto.SeriesDetail;
import me.luucka.mangashelf.catalog.dto.SeriesRequest;
import me.luucka.mangashelf.catalog.dto.SeriesSummary;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/series")
public class SeriesController {

  private final SeriesService service;

  public SeriesController(SeriesService service) {
    this.service = service;
  }

  @GetMapping
  public Page<SeriesSummary> list(@RequestParam(required = false) String q, Pageable pageable) {
    return service.list(q, pageable);
  }

  @GetMapping("/{id}")
  public SeriesDetail detail(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    return service.detail(id, me);
  }

  @PostMapping
  public ResponseEntity<SeriesSummary> create(
      @AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody SeriesRequest body) {
    var s = service.create(body.title(), me);
    var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(s.getId());
    return ResponseEntity.created(location)
        .body(new SeriesSummary(s.getId().toString(), s.getTitle(), 0));
  }

  @PutMapping("/{id}")
  public SeriesSummary rename(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @Valid @RequestBody SeriesRequest body) {
    return service.rename(id, body.title(), me);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    service.delete(id, me);
  }
}
