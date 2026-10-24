package com.shop.orders.orders.domain;

import com.shop.orders.sharedkernel.DomainError;
import com.shop.orders.sharedkernel.DomainError.Kind;

/** Erreurs métier du module, à code stable. Les messages ne portent aucun détail interne. */
public final class OrderErrors {

  public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
  public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
  public static final String MIXED_CURRENCIES = "MIXED_CURRENCIES";
  public static final String CATALOG_UNAVAILABLE = "CATALOG_UNAVAILABLE";
  public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
  public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

  private OrderErrors() {}

  public static DomainError productNotFound() {
    return new DomainError(Kind.VALIDATION, PRODUCT_NOT_FOUND, "Produit inconnu");
  }

  public static DomainError insufficientStock() {
    return new DomainError(Kind.CONFLICT, INSUFFICIENT_STOCK, "Stock insuffisant");
  }

  public static DomainError mixedCurrencies() {
    return new DomainError(
        Kind.CONFLICT, MIXED_CURRENCIES, "Les produits de la commande n'ont pas la même devise");
  }

  public static DomainError catalogUnavailable() {
    return new DomainError(
        Kind.UNAVAILABLE, CATALOG_UNAVAILABLE, "Catalogue temporairement indisponible");
  }

  public static DomainError orderNotFound() {
    return new DomainError(Kind.NOT_FOUND, ORDER_NOT_FOUND, "Commande introuvable");
  }

  public static DomainError validation(String message) {
    return new DomainError(Kind.VALIDATION, VALIDATION_ERROR, message);
  }
}
