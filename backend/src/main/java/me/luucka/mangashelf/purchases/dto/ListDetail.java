package me.luucka.mangashelf.purchases.dto;

import java.util.List;

public record ListDetail(
    String id,
    String description,
    int month,
    int year,
    boolean closed,
    List<ItemView> items,
    long subtotalCents,
    long discountCents,
    boolean discountAuto,
    long pointsDiscountCents,
    long totalCents) {}
