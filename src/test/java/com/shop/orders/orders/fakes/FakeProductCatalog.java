package com.shop.orders.orders.fakes;

import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.sharedkernel.Result;
import java.util.HashMap;
import java.util.Map;

/** Catalogue en mémoire, avec un mode « indisponible » et un compteur d'appels. */
public final class FakeProductCatalog implements ProductCatalog {

  private final Map<ProductId, CatalogProduct> products = new HashMap<>();
  private boolean unavailable;
  private int calls;

  /** Ajoute ou remplace un produit (simule un changement de prix chez shop-api). */
  public void put(CatalogProduct product) {
    products.put(product.id(), product);
  }

  public void remove(ProductId id) {
    products.remove(id);
  }

  public void makeUnavailable() {
    unavailable = true;
  }

  public int calls() {
    return calls;
  }

  @Override
  public Result<CatalogProduct> find(ProductId id) {
    calls++;
    if (unavailable) {
      return new Result.Failure<>(OrderErrors.catalogUnavailable());
    }
    CatalogProduct product = products.get(id);
    if (product == null) {
      return new Result.Failure<>(OrderErrors.productNotFound());
    }
    return new Result.Success<>(product);
  }
}
