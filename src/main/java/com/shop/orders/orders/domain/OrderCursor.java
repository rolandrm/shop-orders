package com.shop.orders.orders.domain;

import java.time.Instant;

/** Position dans la liste des commandes d'un client : (date de création, identifiant). */
public record OrderCursor(Instant createdAt, OrderId id) {

  /**
   * Vrai si l'élément vient strictement après ce curseur dans l'ordre d'affichage (récent d'abord).
   */
  public boolean isBefore(Instant otherCreatedAt, OrderId otherId) {
    int byDate = otherCreatedAt.compareTo(createdAt);
    if (byDate != 0) {
      return byDate < 0;
    }
    return otherId.compareTo(id) < 0;
  }
}
