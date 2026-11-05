package me.luucka.mangashelf.purchases;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void chfRoundsToNearestTenCents() {
    var rate = new BigDecimal("1.20");
    assertThat(Money.chf(790, rate)).isEqualTo(950);
    assertThat(Money.chf(995, rate)).isEqualTo(1190);
    assertThat(Money.chf(1250, rate)).isEqualTo(1500);
    assertThat(Money.chf(6400, rate)).isEqualTo(7680);
  }

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
