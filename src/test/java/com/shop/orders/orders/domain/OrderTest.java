package com.shop.orders.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.sharedkernel.DomainError;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OrderTest {

  private static final OrderId ID = new OrderId(new UUID(1, 1));
  private static final CustomerId CUSTOMER = new CustomerId(new UUID(2, 2));
  private static final Instant NOW = Instant.parse("2026-10-22T10:15:30Z");

  private static OrderLine line(long product, String price, String currency, int quantity) {
    return new OrderLine(
        new ProductId(new UUID(3, product)),
        "Produit " + product,
        Money.of(price, currency),
        quantity);
  }

  private static Result<Order> place(List<OrderLine> lines) {
    return Order.place(ID, CUSTOMER, NOW, lines);
  }

  private static DomainError failureOf(Result<Order> result) {
    assertThat(result).isInstanceOf(Result.Failure.class);
    return ((Result.Failure<Order>) result).error();
  }

  @Test
  void t1ComputesLineTotalsAndTotalWithoutEvents() {
    Result<Order> result = place(List.of(line(1, "10.00", "EUR", 2), line(2, "2.50", "EUR", 4)));

    assertThat(result).isInstanceOf(Result.Success.class);
    Order order = ((Result.Success<Order>) result).value();
    assertThat(order.total().amountAsString()).isEqualTo("30.00");
    assertThat(order.total().currency().getCurrencyCode()).isEqualTo("EUR");
    assertThat(order.lines().get(0).lineTotal().amountAsString()).isEqualTo("20.00");
    assertThat(order.lines().get(1).lineTotal().amountAsString()).isEqualTo("10.00");
    assertThat(order.id()).isEqualTo(ID);
    assertThat(order.customerId()).isEqualTo(CUSTOMER);
    assertThat(order.createdAt()).isEqualTo(NOW);
    assertThat(order.pullEvents()).isEmpty();
  }

  @Test
  void t2ZeroLinesIsRejected() {
    assertThat(failureOf(place(List.of())).kind()).isEqualTo(DomainError.Kind.VALIDATION);
  }

  @Test
  void t2QuantityZeroIsRejected() {
    assertThat(failureOf(place(List.of(line(1, "1.00", "EUR", 0)))).kind())
        .isEqualTo(DomainError.Kind.VALIDATION);
  }

  @Test
  void t2Quantity101IsRejectedAnd100IsAccepted() {
    assertThat(failureOf(place(List.of(line(1, "1.00", "EUR", 101)))).kind())
        .isEqualTo(DomainError.Kind.VALIDATION);
    assertThat(place(List.of(line(1, "1.00", "EUR", 100)))).isInstanceOf(Result.Success.class);
  }

  @Test
  void t2DuplicateProductIsRejected() {
    DomainError error =
        failureOf(place(List.of(line(1, "1.00", "EUR", 1), line(1, "1.00", "EUR", 2))));
    assertThat(error.kind()).isEqualTo(DomainError.Kind.VALIDATION);
    assertThat(error.code()).isEqualTo(OrderErrors.VALIDATION_ERROR);
  }

  @Test
  void t2FiftyOneLinesAreRejectedAndFiftyAccepted() {
    List<OrderLine> fiftyOne =
        IntStream.rangeClosed(1, 51).mapToObj(i -> line(i, "1.00", "EUR", 1)).toList();
    assertThat(failureOf(place(fiftyOne)).kind()).isEqualTo(DomainError.Kind.VALIDATION);
    assertThat(place(fiftyOne.subList(0, 50))).isInstanceOf(Result.Success.class);
  }

  @Test
  void t3MixedCurrenciesAreAConflict() {
    DomainError error =
        failureOf(place(List.of(line(1, "10.00", "EUR", 1), line(2, "10.00", "USD", 1))));
    assertThat(error.kind()).isEqualTo(DomainError.Kind.CONFLICT);
    assertThat(error.code()).isEqualTo(OrderErrors.MIXED_CURRENCIES);
  }

  @Test
  void checkItemsValidatesTheRequestBeforeAnyCatalogCall() {
    ProductId p = new ProductId(new UUID(3, 1));
    assertThat(Order.checkItems(List.of(new RequestedItem(p, 1))))
        .isInstanceOf(Result.Success.class);
    assertThat(Order.checkItems(List.of())).isInstanceOf(Result.Failure.class);
    assertThat(Order.checkItems(List.of(new RequestedItem(p, 0))))
        .isInstanceOf(Result.Failure.class);
    assertThat(Order.checkItems(List.of(new RequestedItem(p, 1), new RequestedItem(p, 1))))
        .isInstanceOf(Result.Failure.class);
  }

  @Test
  void restoreKeepsStateWithoutRevalidation() {
    Order order = Order.restore(ID, CUSTOMER, NOW, List.of(), Money.of("0.00", "EUR"));
    assertThat(order.lines()).isEmpty();
    assertThat(order.total().amountAsString()).isEqualTo("0.00");
  }

  @Test
  void linesAreImmutable() {
    Order order = ((Result.Success<Order>) place(List.of(line(1, "1.00", "EUR", 1)))).value();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> order.lines().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
