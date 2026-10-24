package com.shop.orders.orders.domain;

import com.shop.orders.sharedkernel.Money;
import java.time.Instant;

/** Résumé d'une commande pour la liste ; {@code itemCount} est le nombre de lignes. */
public record OrderSummary(OrderId id, Instant createdAt, Money total, int itemCount) {

  public OrderCursor cursor() {
    return new OrderCursor(createdAt, id);
  }
}
