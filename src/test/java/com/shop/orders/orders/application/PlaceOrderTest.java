package com.shop.orders.orders.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.orders.domain.RequestedItem;
import com.shop.orders.orders.fakes.FakeProductCatalog;
import com.shop.orders.orders.fakes.InMemoryOrderRepository;
import com.shop.orders.sharedkernel.DomainError;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceOrderTest {

  private static final Instant NOW = Instant.parse("2026-10-22T10:15:30Z");
  private static final UUID GENERATED = new UUID(7, 7);
  private static final CustomerId CUSTOMER = new CustomerId(new UUID(2, 2));
  private static final ProductId P1 = new ProductId(new UUID(3, 1));
  private static final ProductId P2 = new ProductId(new UUID(3, 2));

  private final FakeProductCatalog catalog = new FakeProductCatalog();
  private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
  private final PlaceOrder placeOrder =
      new PlaceOrder(catalog, repository, Clock.fixed(NOW, ZoneOffset.UTC), () -> GENERATED);
  private final GetOrder getOrder = new GetOrder(repository);

  private void givenCatalog() {
    catalog.put(new CatalogProduct(P1, "Clavier", Money.of("10.00", "EUR"), 10));
    catalog.put(new CatalogProduct(P2, "Souris", Money.of("2.50", "EUR"), 10));
  }

  private static PlaceOrderCommand command(RequestedItem... items) {
    return new PlaceOrderCommand(CUSTOMER, List.of(items));
  }

  private static DomainError failureOf(Result<Order> result) {
    assertThat(result).isInstanceOf(Result.Failure.class);
    return ((Result.Failure<Order>) result).error();
  }

  private static Order valueOf(Result<Order> result) {
    assertThat(result).isInstanceOf(Result.Success.class);
    return ((Result.Success<Order>) result).value();
  }

  @Test
  void t5CopiesCatalogDataAndUsesInjectedClockAndIdGenerator() {
    givenCatalog();

    Order order =
        valueOf(placeOrder.execute(command(new RequestedItem(P1, 2), new RequestedItem(P2, 4))));

    assertThat(order.id()).isEqualTo(new OrderId(GENERATED));
    assertThat(order.createdAt()).isEqualTo(NOW);
    assertThat(order.customerId()).isEqualTo(CUSTOMER);
    assertThat(order.lines()).hasSize(2);
    assertThat(order.lines().get(0).productName()).isEqualTo("Clavier");
    assertThat(order.lines().get(0).unitPrice().amountAsString()).isEqualTo("10.00");
    assertThat(order.lines().get(0).unitPrice().currency().getCurrencyCode()).isEqualTo("EUR");
    assertThat(order.total().amountAsString()).isEqualTo("30.00");
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void t5PriceChangeAfterCreationDoesNotAffectTheStoredOrderAndReadSkipsTheCatalog() {
    givenCatalog();
    Order placed = valueOf(placeOrder.execute(command(new RequestedItem(P1, 2))));
    int callsAfterPlacing = catalog.calls();

    catalog.put(new CatalogProduct(P1, "Clavier", Money.of("12.00", "EUR"), 10));
    Order read = valueOf(getOrder.execute(placed.id(), CUSTOMER));

    assertThat(read.lines().get(0).unitPrice().amountAsString()).isEqualTo("10.00");
    assertThat(read.total().amountAsString()).isEqualTo("20.00");
    assertThat(catalog.calls()).isEqualTo(callsAfterPlacing);
  }

  @Test
  void t6UnknownProductFailsAndLeavesRepositoryEmpty() {
    catalog.put(new CatalogProduct(P1, "Clavier", Money.of("10.00", "EUR"), 10));

    DomainError error =
        failureOf(placeOrder.execute(command(new RequestedItem(P1, 1), new RequestedItem(P2, 1))));

    assertThat(error.code()).isEqualTo(OrderErrors.PRODUCT_NOT_FOUND);
    assertThat(repository.count()).isZero();
  }

  @Test
  void t6InsufficientStockFailsAndLeavesRepositoryEmpty() {
    catalog.put(new CatalogProduct(P1, "Clavier", Money.of("10.00", "EUR"), 1));

    DomainError error = failureOf(placeOrder.execute(command(new RequestedItem(P1, 2))));

    assertThat(error.code()).isEqualTo(OrderErrors.INSUFFICIENT_STOCK);
    assertThat(error.kind()).isEqualTo(DomainError.Kind.CONFLICT);
    assertThat(repository.count()).isZero();
  }

  @Test
  void t6UnavailableCatalogFailsAndLeavesRepositoryEmpty() {
    givenCatalog();
    catalog.makeUnavailable();

    DomainError error = failureOf(placeOrder.execute(command(new RequestedItem(P1, 1))));

    assertThat(error.code()).isEqualTo(OrderErrors.CATALOG_UNAVAILABLE);
    assertThat(error.kind()).isEqualTo(DomainError.Kind.UNAVAILABLE);
    assertThat(repository.count()).isZero();
  }

  @Test
  void mixedCurrenciesFailAsConflictAndLeaveRepositoryEmpty() {
    catalog.put(new CatalogProduct(P1, "Clavier", Money.of("10.00", "EUR"), 10));
    catalog.put(new CatalogProduct(P2, "Mouse", Money.of("2.50", "USD"), 10));

    DomainError error =
        failureOf(placeOrder.execute(command(new RequestedItem(P1, 1), new RequestedItem(P2, 1))));

    assertThat(error.code()).isEqualTo(OrderErrors.MIXED_CURRENCIES);
    assertThat(repository.count()).isZero();
  }

  @Test
  void invalidRequestIsRejectedBeforeAnyCatalogCall() {
    givenCatalog();

    DomainError error = failureOf(placeOrder.execute(command()));

    assertThat(error.code()).isEqualTo(OrderErrors.VALIDATION_ERROR);
    assertThat(catalog.calls()).isZero();
    assertThat(repository.count()).isZero();
  }
}
