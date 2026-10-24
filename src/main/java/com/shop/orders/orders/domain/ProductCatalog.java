package com.shop.orders.orders.domain;

import com.shop.orders.sharedkernel.Result;

/** Port : lecture d'un produit dans le catalogue de shop-api. */
public interface ProductCatalog {

  /**
   * Lit un produit. Échecs : {@code PRODUCT_NOT_FOUND} si le produit est inconnu, {@code
   * CATALOG_UNAVAILABLE} si le catalogue ne répond pas correctement.
   */
  Result<CatalogProduct> find(ProductId id);
}
