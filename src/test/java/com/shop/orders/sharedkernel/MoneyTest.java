package com.shop.orders.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void additionIsExact() {
    Money sum = Money.of("0.1", "EUR").add(Money.of("0.2", "EUR"));
    assertThat(sum.amountAsString()).isEqualTo("0.30");
    assertThat(sum.compareTo(Money.of("0.30", "EUR"))).isZero();
  }

  @Test
  void multiplicationKeepsCurrencyScale() {
    Money product = Money.of("10.00", "EUR").multiply(2);
    assertThat(product.amountAsString()).isEqualTo("20.00");
    assertThat(product.currency().getCurrencyCode()).isEqualTo("EUR");
  }

  @Test
  void differentCurrenciesAreRefused() {
    Money eur = Money.of("1.00", "EUR");
    Money usd = Money.of("1.00", "USD");
    assertThatThrownBy(() -> eur.add(usd)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> eur.compareTo(usd)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void amountWithTooManyDecimalsIsRefusedWithoutRounding() {
    assertThatThrownBy(() -> Money.of("1.005", "EUR")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unreadableAmountOrCurrencyIsRefused() {
    assertThatThrownBy(() -> Money.of("abc", "EUR")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.of("1.00", "XXY")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void amountsWithDifferentTextualScaleAreEqualByValue() {
    assertThat(Money.of("2", "EUR")).isEqualTo(Money.of("2.00", "EUR"));
  }
}
