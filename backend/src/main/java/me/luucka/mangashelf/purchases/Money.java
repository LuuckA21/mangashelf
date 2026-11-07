package me.luucka.mangashelf.purchases;

/** CHF prices are proposed in the frontend (money.ts proposeChf); here only the discount. */
final class Money {

  private Money() {}

  /** Nearest 0.10 (10 cents) of numerator/denominator cents, halves up, integer only. */
  static long roundTo10(long numerator, long denominator) {
    return Math.floorDiv(2 * numerator + 10 * denominator, 20 * denominator) * 10;
  }

  static long discount(long subtotalCents, int percent) {
    return roundTo10(subtotalCents * percent, 100);
  }
}
