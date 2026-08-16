package me.luucka.mangashelf.purchase.dto;

/**
 * A year of purchases.
 *
 * @param volumeCount     lines carrying a franc price; the averages divide
 *                        by this, so a line with no price cannot drag them down
 * @param fullChfCents    before any discount
 * @param discountChfCents what the discounts took off
 * @param netChfCents     what was actually paid
 */
public record YearStats(
        int year,
        int listCount,
        int volumeCount,
        int fullChfCents,
        int discountChfCents,
        int netChfCents,
        int averageFullChfCents,
        int averageNetChfCents
) {
}
