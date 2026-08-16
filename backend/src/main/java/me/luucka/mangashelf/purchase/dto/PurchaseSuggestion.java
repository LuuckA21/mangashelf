package me.luucka.mangashelf.purchase.dto;

/**
 * The next volume of a run already bought, with the prices last paid.
 *
 * <p>A new volume of an ongoing series is the same line as last month with
 * the number moved on by one — which is exactly what this carries, so that
 * adding it costs a click instead of four fields.
 *
 * @param volumeNumber one past the highest number bought so far
 * @param lastBoughtIn name of the list the prices come from
 */
public record PurchaseSuggestion(
        Long seriesId,
        String seriesName,
        String publisher,
        String mangaTitle,
        Short volumeNumber,
        Integer priceEurCents,
        Integer priceChfCents,
        String lastBoughtIn
) {
}
