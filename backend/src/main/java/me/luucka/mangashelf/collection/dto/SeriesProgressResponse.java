package me.luucka.mangashelf.collection.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How far along an edition is.
 *
 * @param upTo           the highest number the shelf reaches: the edition's
 *                       declared total when it has one, otherwise the highest
 *                       volume owned — nobody knows what a publisher has
 *                       released, so the shelf stops where the evidence does
 * @param missingNumbers gaps below {@code upTo}
 */
public record SeriesProgressResponse(
        Long seriesId,
        String seriesName,
        String mangaTitle,
        Short declaredTotal,
        int upTo,
        int ownedCount,
        List<Short> ownedNumbers,
        List<Short> missingNumbers
) {

    /**
     * Builds the view from the owned numbers alone.
     *
     * <p>A gap inside a run needs no catalogue: 1-45 and 47 says the 46 is
     * missing. Only what lies past the highest owned number is unknowable,
     * and that is what the declared total is for when it is set.
     */
    public static SeriesProgressResponse of(Long seriesId, String seriesName,
                                            String mangaTitle, Short declaredTotal,
                                            List<Short> owned) {
        int highestOwned = owned.isEmpty() ? 0 : owned.getLast();
        int upTo = declaredTotal != null ? Math.max(declaredTotal, highestOwned) : highestOwned;

        // Counted in int: a short reaching 32767 overflows on the next
        // increment and the loop never ends. A set rather than the list,
        // because contains on a list makes this quadratic.
        Set<Short> ownedSet = new HashSet<>(owned);
        List<Short> missing = new ArrayList<>();
        for (int n = 1; n <= upTo; n++) {
            short number = (short) n;
            if (!ownedSet.contains(number)) missing.add(number);
        }

        return new SeriesProgressResponse(seriesId, seriesName, mangaTitle,
                declaredTotal, upTo, owned.size(), owned, missing);
    }
}
