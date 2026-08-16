package me.luucka.mangashelf.purchase.dto;

import me.luucka.mangashelf.purchase.PurchaseItem;

import java.time.LocalDate;

public record PurchaseItemResponse(
        Long id,
        Long seriesId,
        String seriesName,
        String publisher,
        String mangaTitle,
        Short volumeNumber,
        LocalDate releaseDate,
        Integer priceEurCents,
        Integer priceChfCents,
        boolean reserved
) {

    public static PurchaseItemResponse from(PurchaseItem item) {
        var series = item.getSeries();
        return new PurchaseItemResponse(
                item.getId(),
                series.getId(),
                series.getName(),
                series.getPublisher(),
                series.getManga().displayTitle(),
                item.getVolumeNumber(),
                item.getReleaseDate(),
                item.getPriceEurCents(),
                item.getPriceChfCents(),
                item.isReserved());
    }
}
