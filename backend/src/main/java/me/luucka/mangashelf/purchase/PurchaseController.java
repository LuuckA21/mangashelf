package me.luucka.mangashelf.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import me.luucka.mangashelf.purchase.dto.PurchaseItemRequest;
import me.luucka.mangashelf.purchase.dto.PurchaseListRequest;
import me.luucka.mangashelf.purchase.dto.PurchaseListResponse;
import me.luucka.mangashelf.purchase.dto.PurchaseListSummary;
import me.luucka.mangashelf.purchase.dto.PurchaseStats;
import me.luucka.mangashelf.purchase.dto.PurchaseSuggestion;
import me.luucka.mangashelf.purchase.dto.TransferResult;
import me.luucka.mangashelf.user.UserPrincipal;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Purchase lists. Open to any signed-in user: a list is personal, like the
 * collection, so none of this is administrator work.
 */
@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchases;

    public PurchaseController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    @GetMapping
    public List<PurchaseListSummary> listAll(@AuthenticationPrincipal UserPrincipal principal) {
        return purchases.listAll(principal);
    }

    /** Totals and averages by year, across every list. */
    @GetMapping("/stats")
    public PurchaseStats stats(@AuthenticationPrincipal UserPrincipal principal) {
        return purchases.stats(principal);
    }

    @GetMapping("/{id}")
    public PurchaseListResponse get(@PathVariable Long id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.get(id, principal);
    }

    /** Next volumes of runs already bought, with the prices last paid. */
    @GetMapping("/{id}/suggestions")
    public List<PurchaseSuggestion> suggestions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.suggestions(id, principal);
    }

    @PostMapping
    public ResponseEntity<PurchaseListResponse> create(
            @Valid @RequestBody PurchaseListRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchases.create(request, principal));
    }

    @PutMapping("/{id}")
    public PurchaseListResponse update(@PathVariable Long id,
                                       @Valid @RequestBody PurchaseListRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        purchases.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    /** Marks every purchased volume of the list as owned. Repeatable. */
    @PostMapping("/{id}/to-collection")
    public TransferResult toCollection(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.toCollection(id, principal);
    }

    /** Marks the list paid, or reopens it. */
    @PutMapping("/{id}/paid")
    public PurchaseListResponse setPaid(@PathVariable Long id,
                                        @Valid @RequestBody PaidRequest body,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.setPaid(id, body.paid(), principal);
    }

    /** Marks one line as bought, or takes the mark back. */
    @PutMapping("/{id}/items/{itemId}/purchased")
    public PurchaseListResponse setPurchased(@PathVariable Long id,
                                             @PathVariable Long itemId,
                                             @Valid @RequestBody PurchasedRequest body,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.setPurchased(id, itemId,
                body.purchased(), principal);
    }

    /** Moves the unbought lines of another list into this one. */
    @PostMapping("/{id}/carry-over/{sourceId}")
    public Map<String, Integer> carryOver(@PathVariable Long id,
                                          @PathVariable Long sourceId,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("moved", purchases.carryOver(id, sourceId, principal));
    }

    /** Marks one line as set aside at the shop, or clears it. */
    @PutMapping("/{id}/items/{itemId}/reserved")
    public PurchaseListResponse setReserved(@PathVariable Long id,
                                            @PathVariable Long itemId,
                                            @Valid @RequestBody ReservedRequest body,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.setReserved(id, itemId,
                body.reserved(), principal);
    }

    @PostMapping("/{id}/items")
    public PurchaseListResponse addItem(@PathVariable Long id,
                                        @Valid @RequestBody PurchaseItemRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.addItem(id, request, principal);
    }

    @PutMapping("/{id}/items/{itemId}")
    public PurchaseListResponse updateItem(@PathVariable Long id,
                                           @PathVariable Long itemId,
                                           @Valid @RequestBody PurchaseItemRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.updateItem(id, itemId, request, principal);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public PurchaseListResponse removeItem(@PathVariable Long id,
                                           @PathVariable Long itemId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return purchases.removeItem(id, itemId, principal);
    }

    public record PaidRequest(@NotNull Boolean paid) {}
    public record PurchasedRequest(@NotNull Boolean purchased) {}
    public record ReservedRequest(@NotNull Boolean reserved) {}
}
