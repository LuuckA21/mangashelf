package me.luucka.mangashelf.purchases;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void discountRoundsToNearestTenCents() {
    assertThat(Money.discount(12610, 10)).isEqualTo(1260);
    assertThat(Money.discount(16490, 10)).isEqualTo(1650);
    assertThat(Money.discount(24100, 10)).isEqualTo(2410);
  }

  @Test
  void halvesRoundUp() {
    assertThat(Money.roundTo10(5, 1)).isEqualTo(10);
    assertThat(Money.roundTo10(4, 1)).isEqualTo(0);
  }
}
