package me.luucka.mangashelf.collection.dto;

import java.util.List;

/**
 * One edition on a user's shelf, with enough context to say how far along
 * the run is.
 *
 * @param totalVolumes   volumes catalogued for the edition, not volumes owned
 * @param missingNumbers the gaps — what is left to buy
 */
public record EditionSummary(
        Long seriesId,
        String seriesName,
        String publisher,
        Long mangaId,
        String mangaTitle,
        String coverUrl,
        int totalVolumes,
        int ownedCount,
        List<Short> ownedNumbers,
        List<Short> missingNumbers
) {

    /** True when the catalogue lists volumes this user does not have. */
    public boolean complete() {
        return missingNumbers.isEmpty();
    }
}
