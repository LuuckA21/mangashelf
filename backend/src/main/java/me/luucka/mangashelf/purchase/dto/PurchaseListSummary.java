package me.luucka.mangashelf.purchase.dto;

/** A list as it appears in the index: name, size and what it costs. */
public record PurchaseListSummary(
        Long id,
        String name,
        int itemCount,
        int totalChfCents
) {
}
