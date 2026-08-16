package me.luucka.mangashelf.purchase.dto;

import java.util.List;

/** Purchases by year, newest first, with the running totals underneath. */
public record PurchaseStats(
        List<YearStats> years,
        int listCount,
        int volumeCount,
        int fullChfCents,
        int discountChfCents,
        int netChfCents,
        int averageFullChfCents,
        int averageNetChfCents
) {
}
