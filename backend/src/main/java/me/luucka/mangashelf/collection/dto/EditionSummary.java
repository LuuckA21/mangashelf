package me.luucka.mangashelf.collection.dto;

import java.util.List;

/**
 * One edition on a user's shelf.
 *
 * @param upTo           where the shelf stops: the declared total when the
 *                       edition has one, otherwise the highest owned volume
 * @param missingNumbers the gaps — what is left to find
 */
public record EditionSummary(
        Long seriesId,
        String seriesName,
        String publisher,
        Long mangaId,
        String mangaTitle,
        String coverUrl,
        Short declaredTotal,
        int upTo,
        int ownedCount,
        List<Short> ownedNumbers,
        List<Short> missingNumbers
) {
}
