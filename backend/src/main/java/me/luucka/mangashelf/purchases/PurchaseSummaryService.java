package me.luucka.mangashelf.purchases;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import me.luucka.mangashelf.purchases.dto.ListSummary;
import me.luucka.mangashelf.purchases.dto.SpendingSummary;
import me.luucka.mangashelf.purchases.dto.SpendingSummary.MonthSpend;
import me.luucka.mangashelf.purchases.dto.SpendingSummary.SpendTotals;
import me.luucka.mangashelf.purchases.dto.SpendingSummary.YearSpend;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseSummaryService {

  private final PurchaseService purchases;

  public PurchaseSummaryService(PurchaseService purchases) {
    this.purchases = purchases;
  }

  @Transactional(readOnly = true)
  public SpendingSummary summary(UUID userId, Integer requested) {
    var lists = purchases.lists(userId);
    var years = lists.stream().map(ListSummary::year).distinct().sorted().toList();
    Integer year = years.isEmpty() ? null : years.contains(requested) ? requested : years.getLast();

    var months =
        IntStream.rangeClosed(1, 12)
            .mapToObj(m ->
                Spend.of(
                    lists.stream()
                        .filter(l -> year != null && l.year() == year && l.month() == m)
                        .toList()))
            .toList();
    var monthTotals = totals(months);
    var monthRows =
        IntStream.rangeClosed(1, 12)
            .mapToObj(m -> {
              var s = months.get(m - 1);
              return new MonthSpend(
                  m, s.spent(), s.upcoming(), s.hasLists(), s.total() > monthTotals.averageCents());
            })
            .toList();

    var perYear =
        years.stream()
            .map(y -> Spend.of(lists.stream().filter(l -> l.year() == y).toList()))
            .toList();
    var yearTotals = totals(perYear);
    var byYear =
        IntStream.range(0, years.size())
            .mapToObj(i -> {
              var s = perYear.get(i);
              return new YearSpend(
                  years.get(i), s.spent(), s.upcoming(), s.total() > yearTotals.averageCents());
            })
            .toList();
    return new SpendingSummary(years, year, monthRows, monthTotals, byYear, yearTotals);
  }

  /** Closed lists are spent, open ones upcoming. */
  private record Spend(long spent, long upcoming, boolean hasLists) {
    static Spend of(List<ListSummary> lists) {
      long spent = 0;
      long upcoming = 0;
      for (var l : lists) {
        if (l.closed()) {
          spent += l.totalCents();
        } else {
          upcoming += l.totalCents();
        }
      }
      return new Spend(spent, upcoming, !lists.isEmpty());
    }

    long total() {
      return spent + upcoming;
    }
  }

  private static SpendTotals totals(List<Spend> spends) {
    long spent = spends.stream().mapToLong(Spend::spent).sum();
    long upcoming = spends.stream().mapToLong(Spend::upcoming).sum();
    long n = spends.stream().filter(s -> s.total() > 0).count();
    long total = spent + upcoming;
    // integer half-up rounding; totals are never negative
    long average = n == 0 ? 0 : (2 * total + n) / (2 * n);
    return new SpendTotals(total, average, spent, upcoming);
  }
}
