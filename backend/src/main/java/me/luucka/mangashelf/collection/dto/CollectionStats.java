package me.luucka.mangashelf.collection.dto;

import java.util.List;

public record CollectionStats(
    long editions,
    long series,
    long ownedVolumes,
    long totalVolumes,
    long completedEditions,
    List<StatBar> byPublisher,
    List<StatBar> byType,
    List<StatBar> byStatus) {}
