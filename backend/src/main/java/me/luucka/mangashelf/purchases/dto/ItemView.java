package me.luucka.mangashelf.purchases.dto;

import java.time.LocalDate;
import java.util.List;

public record ItemView(
    String id,
    String editionId,
    String seriesId,
    String seriesTitle,
    String editionName,
    int editionVolumeCount,
    int volume,
    LocalDate releaseDate,
    long priceEurCents,
    long priceChfCents,
    boolean booked,
    List<String> alsoIn) {}
