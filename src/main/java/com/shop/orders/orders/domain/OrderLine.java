package com.shop.orders.orders.domain;

import com.shop.orders.sharedkernel.Money;

/** Ligne de commande : nom et prix du produit copiés au moment de l'achat. */
public record OrderLine(ProductId productId, String productName, Money unitPrice, int quantity) {

  public Money lineTotal() {
    return unitPrice.multiply(quantity);
  }
}
