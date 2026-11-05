package me.luucka.mangashelf.purchases;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import me.luucka.mangashelf.purchases.dto.ItemPatch;
import me.luucka.mangashelf.purchases.dto.ItemRequest;
import me.luucka.mangashelf.purchases.dto.ListDetail;
import me.luucka.mangashelf.purchases.dto.ListPatch;
import me.luucka.mangashelf.purchases.dto.ListRequest;
import me.luucka.mangashelf.purchases.dto.ListSummary;
import me.luucka.mangashelf.purchases.dto.PurchaseSettings;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PurchaseController {

  private final PurchaseService service;

  public PurchaseController(PurchaseService service) {
    this.service = service;
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
  @ResponseStatus(HttpStatus.CREATED)
  public ListDetail create(
      @AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody ListRequest body) {
    return service.create(me.id(), body);
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
}
