package com.shop.orders.orders.domain;

import java.util.UUID;

/** Identifiant d'une commande. */
public record OrderId(UUID value) implements Comparable<OrderId> {

  /** Ordre des octets non signés, celui du type {@code uuid} de PostgreSQL. */
  @Override
  public int compareTo(OrderId other) {
    int high =
        Long.compareUnsigned(value.getMostSignificantBits(), other.value.getMostSignificantBits());
    if (high != 0) {
      return high;
    }
    return Long.compareUnsigned(
        value.getLeastSignificantBits(), other.value.getLeastSignificantBits());
  }
}
