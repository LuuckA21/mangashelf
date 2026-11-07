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

    var monthLists = lists.stream().filter(l -> year != null && l.year() == year).toList();
    var months =
        IntStream.rangeClosed(1, 12)
            .mapToObj(m -> monthLists.stream().filter(l -> l.month() == m).toList())
            .toList();
    var monthTotals = totals(months.stream().map(PurchaseSummaryService::sum).toList());
    var monthRows =
        IntStream.rangeClosed(1, 12)
            .mapToObj(m -> {
              var sum = sum(months.get(m - 1));
              return new MonthSpend(
                  m,
                  sum[0],
                  sum[1],
                  !months.get(m - 1).isEmpty(),
                  sum[0] + sum[1] > monthTotals.averageCents());
            })
            .toList();

    var yearSums = years.stream().map(y -> sum(lists.stream().filter(l -> l.year() == y).toList()));
    var sums = yearSums.toList();
    var yearTotals = totals(sums);
    var byYear =
        IntStream.range(0, years.size())
            .mapToObj(i ->
                new YearSpend(
                    years.get(i),
                    sums.get(i)[0],
                    sums.get(i)[1],
                    sums.get(i)[0] + sums.get(i)[1] > yearTotals.averageCents()))
            .toList();
    return new SpendingSummary(years, year, monthRows, monthTotals, byYear, yearTotals);
  }

  /** {spent, upcoming}: closed lists are spent, open ones upcoming. */
  private static long[] sum(List<ListSummary> lists) {
    long spent = 0;
    long upcoming = 0;
    for (var l : lists) {
      if (l.closed()) {
        spent += l.totalCents();
      } else {
        upcoming += l.totalCents();
      }
    }
    return new long[] {spent, upcoming};
  }

  private static SpendTotals totals(List<long[]> sums) {
    long spent = sums.stream().mapToLong(s -> s[0]).sum();
    long upcoming = sums.stream().mapToLong(s -> s[1]).sum();
    long n = sums.stream().filter(s -> s[0] + s[1] > 0).count();
    long total = spent + upcoming;
    // integer half-up rounding; totals are never negative
    long average = n == 0 ? 0 : (2 * total + n) / (2 * n);
    return new SpendTotals(total, average, spent, upcoming);
  }
}
