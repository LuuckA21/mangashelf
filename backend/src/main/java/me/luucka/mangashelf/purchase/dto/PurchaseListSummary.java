package me.luucka.mangashelf.purchase.dto;

import java.time.Instant;

/** A list as it appears in the index: name, period, progress and cost. */
public record PurchaseListSummary(
        Long id,
        String name,
        Short periodYear,
        Short periodMonth,
        Instant paidAt,
        int itemCount,
        int reservedCount,
        int purchasedCount,
        int totalChfCents
) {
}
