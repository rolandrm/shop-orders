package com.shop.orders.orders.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderLine;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.orders.fakes.InMemoryOrderRepository;
import com.shop.orders.sharedkernel.DomainError;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListOrdersTest {

  private static final CustomerId C1 = new CustomerId(new UUID(2, 1));
  private static final CustomerId C2 = new CustomerId(new UUID(2, 2));
  private static final Instant T0 = Instant.parse("2026-10-22T10:00:00Z");

  private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
  private final ListOrders listOrders = new ListOrders(repository);

  private Order givenOrder(long n, CustomerId customer, Instant createdAt, int lineCount) {
    List<OrderLine> lines =
        java.util.stream.LongStream.rangeClosed(1, lineCount)
            .mapToObj(
                i ->
                    new OrderLine(
                        new ProductId(new UUID(3, i)), "P" + i, Money.of("1.00", "EUR"), 1))
            .toList();
    Order order =
        ((Result.Success<Order>)
                Order.place(new OrderId(new UUID(1, n)), customer, createdAt, lines))
            .value();
    repository.save(order);
    return order;
  }

  private static OrderPage pageOf(Result<OrderPage> result) {
    assertThat(result).isInstanceOf(Result.Success.class);
    return ((Result.Success<OrderPage>) result).value();
  }

  @Test
  void pagesAreNewestFirstWithoutNextCursorOnTheLastPage() {
    Order oldest = givenOrder(1, C1, T0, 1);
    Order middle = givenOrder(2, C1, T0.plusSeconds(1), 1);
    Order newest = givenOrder(3, C1, T0.plusSeconds(2), 1);
    givenOrder(4, C2, T0.plusSeconds(3), 1);

    OrderPage first = pageOf(listOrders.execute(C1, null, 2));
    assertThat(first.items()).extracting(s -> s.id()).containsExactly(newest.id(), middle.id());
    assertThat(first.nextCursor()).isNotNull();

    OrderPage second = pageOf(listOrders.execute(C1, first.nextCursor(), 2));
    assertThat(second.items()).extracting(s -> s.id()).containsExactly(oldest.id());
    assertThat(second.nextCursor()).isNull();
  }

  @Test
  void exactlyOneFullPageHasNoNextCursor() {
    givenOrder(1, C1, T0, 1);
    givenOrder(2, C1, T0.plusSeconds(1), 1);

    OrderPage page = pageOf(listOrders.execute(C1, null, 2));

    assertThat(page.items()).hasSize(2);
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void itemCountIsTheNumberOfLines() {
    givenOrder(1, C1, T0, 3);

    OrderPage page = pageOf(listOrders.execute(C1, null, 20));

    assertThat(page.items())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.itemCount()).isEqualTo(3);
              assertThat(s.total().amountAsString()).isEqualTo("3.00");
            });
  }

  @Test
  void customerWithoutOrderGetsAnEmptyPage() {
    givenOrder(1, C2, T0, 1);

    OrderPage page = pageOf(listOrders.execute(C1, null, 20));

    assertThat(page.items()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void limitOutOfBoundsIsAValidationFailure() {
    for (int limit : new int[] {0, 101, -1}) {
      Result<OrderPage> result = listOrders.execute(C1, null, limit);
      assertThat(result).isInstanceOf(Result.Failure.class);
      DomainError error = ((Result.Failure<OrderPage>) result).error();
      assertThat(error.code()).isEqualTo(OrderErrors.VALIDATION_ERROR);
      assertThat(error.kind()).isEqualTo(DomainError.Kind.VALIDATION);
    }
  }
}
