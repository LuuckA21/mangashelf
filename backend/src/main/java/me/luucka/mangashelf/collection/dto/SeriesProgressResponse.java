package me.luucka.mangashelf.collection.dto;

import java.util.List;

/**
 * How complete a user's edition is.
 *
 * @param totalVolumes   volumes catalogued for this edition
 * @param ownedNumbers   the numbers held
 * @param missingNumbers the gaps — the shopping list
 */
public record SeriesProgressResponse(
        Long seriesId,
        String seriesName,
        String mangaTitle,
        int totalVolumes,
        int ownedCount,
        List<Short> ownedNumbers,
        List<Short> missingNumbers
) {
}
