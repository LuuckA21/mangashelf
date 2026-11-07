package me.luucka.mangashelf.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import me.luucka.mangashelf.purchases.dto.ItemPatch;
import me.luucka.mangashelf.purchases.dto.ItemRequest;
import me.luucka.mangashelf.purchases.dto.ListDetail;
import me.luucka.mangashelf.purchases.dto.ListPatch;
import me.luucka.mangashelf.purchases.dto.ListRequest;
import me.luucka.mangashelf.purchases.dto.ListSummary;
import me.luucka.mangashelf.purchases.dto.MoveRequest;
import me.luucka.mangashelf.purchases.dto.Proposal;
import me.luucka.mangashelf.purchases.dto.PurchaseSettings;
import me.luucka.mangashelf.purchases.dto.SpendingSummary;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class PurchaseController {

  private final PurchaseService service;
  private final PurchaseSummaryService summaries;

  public PurchaseController(PurchaseService service, PurchaseSummaryService summaries) {
    this.service = service;
    this.summaries = summaries;
  }

  @GetMapping("/api/v1/purchase-summary")
  public SpendingSummary summary(
      @AuthenticationPrincipal AppUserPrincipal me,
      @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
    return summaries.summary(me.id(), year);
  }

  @GetMapping("/api/v1/account/purchase-settings")
  public PurchaseSettings settings(@AuthenticationPrincipal AppUserPrincipal me) {
    return service.settings(me.id());
  }

  @PutMapping("/api/v1/account/purchase-settings")
  public PurchaseSettings updateSettings(
      @AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody PurchaseSettings body) {
    return service.updateSettings(me.id(), body);
  }

  private static final String LISTS = "/api/v1/purchase-lists";

  @GetMapping(LISTS)
  public List<ListSummary> lists(@AuthenticationPrincipal AppUserPrincipal me) {
    return service.lists(me.id());
  }

  @PostMapping(LISTS)
  public ResponseEntity<ListDetail> create(
      @AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody ListRequest body) {
    var list = service.create(me.id(), body);
    var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(list.id());
    return ResponseEntity.created(location).body(list);
  }

  @GetMapping(LISTS + "/{id}")
  public ListDetail get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    return service.get(me.id(), id);
  }

  @PatchMapping(LISTS + "/{id}")
  public ListDetail update(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @Valid @RequestBody ListPatch body) {
    return service.update(me.id(), id, body);
  }

  @DeleteMapping(LISTS + "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    service.delete(me.id(), id);
  }

  @PostMapping(LISTS + "/{id}/items")
  @ResponseStatus(HttpStatus.CREATED)
  public ListDetail addItem(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @Valid @RequestBody ItemRequest body) {
    return service.addItem(me.id(), id, body);
  }

  @PatchMapping(LISTS + "/{id}/items/{itemId}")
  public ListDetail updateItem(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @Valid @RequestBody ItemPatch body) {
    return service.updateItem(me.id(), id, itemId, body);
  }

  @DeleteMapping(LISTS + "/{id}/items/{itemId}")
  public ListDetail deleteItem(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @PathVariable UUID itemId) {
    return service.deleteItem(me.id(), id, itemId);
  }

  @PostMapping(LISTS + "/{id}/close")
  public ListDetail close(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    return service.close(me.id(), id);
  }

  @PostMapping(LISTS + "/{id}/reopen")
  public ListDetail reopen(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
    return service.reopen(me.id(), id);
  }

  @PostMapping(LISTS + "/{id}/items/move")
  public ListDetail move(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @Valid @RequestBody MoveRequest body) {
    return service.move(me.id(), id, body);
  }

  @GetMapping(LISTS + "/{id}/proposal")
  public Proposal proposal(
      @AuthenticationPrincipal AppUserPrincipal me,
      @PathVariable UUID id,
      @RequestParam UUID editionId) {
    return service.proposal(me.id(), id, editionId);
  }
}
