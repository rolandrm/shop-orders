package com.shop.orders.sharedkernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Montant d'argent : {@link BigDecimal} construit depuis une chaîne et {@link Currency}. L'échelle
 * est celle de la devise ; aucun arrondi implicite n'est appliqué.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

  public Money {
    int digits = currency.getDefaultFractionDigits();
    if (digits < 0) {
      throw new IllegalArgumentException("Devise sans décimales définies : " + currency);
    }
    try {
      amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Montant incompatible avec la devise " + currency, e);
    }
  }

  /** Construit un montant depuis une chaîne décimale et un code de devise ISO 4217. */
  public static Money of(String amount, String currencyCode) {
    return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money multiply(int factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
  }

  /** Comparaison numérique (jamais par {@code equals}) ; les devises doivent être identiques. */
  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  /** Montant en notation décimale simple, par exemple {@code 20.00}. */
  public String amountAsString() {
    return amount.toPlainString();
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Devises différentes : " + currency + " et " + other.currency);
    }
  }
}
