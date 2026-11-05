package me.luucka.mangashelf.purchases;

import jakarta.validation.Valid;
import me.luucka.mangashelf.purchases.dto.PurchaseSettings;
import me.luucka.mangashelf.users.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
}
