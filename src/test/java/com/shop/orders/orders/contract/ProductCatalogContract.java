package com.shop.orders.orders.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.sharedkernel.DomainError;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Suite de contrat du port {@link ProductCatalog}, exécutée contre le faux (unitaire) et contre
 * l'adaptateur shop-api (intégration). Chaque implémentation fournit ses mises en scène.
 */
public abstract class ProductCatalogContract {

  protected static final CatalogProduct KNOWN =
      new CatalogProduct(
          new ProductId(UUID.fromString("018f0000-0000-7000-8000-000000000001")),
          "Clavier",
          Money.of("10.00", "EUR"),
          7);

  protected static final ProductId UNKNOWN =
      new ProductId(UUID.fromString("018f0000-0000-7000-8000-0000000000ff"));

  protected abstract ProductCatalog catalog();

  /** Le catalogue connaît ce produit. */
  protected abstract void givenProduct(CatalogProduct product);

  /** Le catalogue ne connaît pas ce produit. */
  protected abstract void givenNoProduct(ProductId id);

  /** Le catalogue ne répond pas correctement. */
  protected abstract void givenCatalogFailing();

  @Test
  public void readsNamePriceCurrencyAndStock() {
    givenProduct(KNOWN);

    Result<CatalogProduct> result = catalog().find(KNOWN.id());

    CatalogProduct product = success(result);
    assertThat(product.id()).isEqualTo(KNOWN.id());
    assertThat(product.name()).isEqualTo("Clavier");
    assertThat(product.price().amountAsString()).isEqualTo("10.00");
    assertThat(product.price().currency().getCurrencyCode()).isEqualTo("EUR");
    assertThat(product.stock()).isEqualTo(7);
  }

  @Test
  public void unknownProductIsReportedAsNotFound() {
    givenNoProduct(UNKNOWN);

    DomainError error = failure(catalog().find(UNKNOWN));

    assertThat(error.code()).isEqualTo(OrderErrors.PRODUCT_NOT_FOUND);
    assertThat(error.kind()).isEqualTo(DomainError.Kind.VALIDATION);
  }

  @Test
  public void failingCatalogIsReportedAsUnavailable() {
    givenCatalogFailing();

    DomainError error = failure(catalog().find(KNOWN.id()));

    assertThat(error.code()).isEqualTo(OrderErrors.CATALOG_UNAVAILABLE);
    assertThat(error.kind()).isEqualTo(DomainError.Kind.UNAVAILABLE);
  }

  private static CatalogProduct success(Result<CatalogProduct> result) {
    assertThat(result).isInstanceOf(Result.Success.class);
    return ((Result.Success<CatalogProduct>) result).value();
  }

  private static DomainError failure(Result<CatalogProduct> result) {
    assertThat(result).isInstanceOf(Result.Failure.class);
    return ((Result.Failure<CatalogProduct>) result).error();
  }
}
