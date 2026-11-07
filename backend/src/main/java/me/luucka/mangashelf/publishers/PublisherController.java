package me.luucka.mangashelf.publishers;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import me.luucka.mangashelf.publishers.dto.PublisherRequest;
import me.luucka.mangashelf.publishers.dto.PublisherResponse;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/publishers")
public class PublisherController {

  private final PublisherService service;

  public PublisherController(PublisherService service) {
    this.service = service;
  }

  @GetMapping
  public List<PublisherResponse> list() {
    return service.list();
  }

  @PostMapping
  public ResponseEntity<PublisherResponse> create(
      @AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody PublisherRequest body) {
    var p = service.create(body.name(), me);
    var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(p.id());
    return ResponseEntity.created(location).body(p);
  }

  @PatchMapping("/{id}")
  public PublisherResponse rename(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @Valid @RequestBody PublisherRequest body) {
    return service.rename(id, body.name(), me);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    service.delete(id, me);
    return ResponseEntity.noContent().build();
  }
}
