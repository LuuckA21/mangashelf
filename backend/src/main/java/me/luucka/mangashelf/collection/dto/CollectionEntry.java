package me.luucka.mangashelf.collection.dto;

import java.util.List;
import me.luucka.mangashelf.catalog.EditionStatus;
import me.luucka.mangashelf.catalog.EditionType;

public record CollectionEntry(
    String editionId,
    String seriesId,
    String seriesTitle,
    String editionName,
    String publisher,
    EditionType type,
    EditionStatus status,
    int volumeCount,
    boolean hasCover,
    List<Integer> owned) {}
