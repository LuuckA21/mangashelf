package me.luucka.mangashelf.catalog.dto;

import me.luucka.mangashelf.catalog.EditionStatus;
import me.luucka.mangashelf.catalog.EditionType;

public record EditionResponse(
    String id,
    String seriesId,
    String seriesTitle,
    String name,
    String publisherId,
    String publisher,
    EditionType type,
    EditionStatus status,
    int volumeCount,
    boolean hasCover,
    boolean canEditCover,
    long version,
    long collectionCount,
    boolean inMyCollection) {}
