package me.luucka.mangashelf.purchases.dto;

import java.util.List;

public record SpendingSummary(
    List<Integer> years,
    Integer year,
    List<MonthSpend> months,
    SpendTotals monthTotals,
    List<YearSpend> byYear,
    SpendTotals yearTotals) {

  public record MonthSpend(
      int month, long spentCents, long upcomingCents, boolean hasLists, boolean aboveAverage) {}

  public record YearSpend(int year, long spentCents, long upcomingCents, boolean aboveAverage) {}

  public record SpendTotals(
      long totalCents, long averageCents, long spentCents, long upcomingCents) {}
}
