package me.luucka.mangashelf.catalog.dto;

/**
 * One volume number, tagged with the edition it belongs to.
 *
 * <p>Used to pull the numbers of several editions in a single query, so that
 * building a collection summary does not turn into one query per edition.
 */
public record SeriesVolumeNumber(Long seriesId, Short number) {
}
