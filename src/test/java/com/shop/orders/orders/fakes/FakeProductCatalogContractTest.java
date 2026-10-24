package com.shop.orders.orders.fakes;

import com.shop.orders.orders.contract.ProductCatalogContract;
import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.orders.domain.ProductId;

class FakeProductCatalogContractTest extends ProductCatalogContract {

  private final FakeProductCatalog fake = new FakeProductCatalog();

  @Override
  protected ProductCatalog catalog() {
    return fake;
  }

  @Override
  protected void givenProduct(CatalogProduct product) {
    fake.put(product);
  }

  @Override
  protected void givenNoProduct(ProductId id) {
    fake.remove(id);
  }

  @Override
  protected void givenCatalogFailing() {
    fake.makeUnavailable();
  }
}
