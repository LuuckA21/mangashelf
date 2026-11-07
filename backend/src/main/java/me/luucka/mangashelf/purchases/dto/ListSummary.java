package me.luucka.mangashelf.purchases.dto;

public record ListSummary(
    String id,
    String description,
    int month,
    int year,
    boolean closed,
    int itemCount,
    int unbookedCount,
    long totalCents) {}
