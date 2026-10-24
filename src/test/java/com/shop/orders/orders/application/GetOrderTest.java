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

class GetOrderTest {

  private static final CustomerId C1 = new CustomerId(new UUID(2, 1));
  private static final CustomerId C2 = new CustomerId(new UUID(2, 2));

  private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
  private final GetOrder getOrder = new GetOrder(repository);

  private Order givenOrderOf(CustomerId customer) {
    Result<Order> placed =
        Order.place(
            new OrderId(new UUID(1, 1)),
            customer,
            Instant.parse("2026-10-22T10:00:00Z"),
            List.of(
                new OrderLine(
                    new ProductId(new UUID(3, 1)), "Clavier", Money.of("10.00", "EUR"), 2)));
    Order order = ((Result.Success<Order>) placed).value();
    repository.save(order);
    return order;
  }

  @Test
  void ownerReadsTheOrder() {
    Order order = givenOrderOf(C1);

    Result<Order> result = getOrder.execute(order.id(), C1);

    assertThat(result).isInstanceOf(Result.Success.class);
    assertThat(((Result.Success<Order>) result).value().total().amountAsString())
        .isEqualTo("20.00");
  }

  @Test
  void otherCustomerGetsTheSameFailureAsAnUnknownId() {
    Order order = givenOrderOf(C1);

    Result<Order> otherCustomer = getOrder.execute(order.id(), C2);
    Result<Order> unknown = getOrder.execute(new OrderId(new UUID(9, 9)), C1);

    assertThat(otherCustomer).isInstanceOf(Result.Failure.class);
    DomainError error = ((Result.Failure<Order>) otherCustomer).error();
    assertThat(error.code()).isEqualTo(OrderErrors.ORDER_NOT_FOUND);
    assertThat(error.kind()).isEqualTo(DomainError.Kind.NOT_FOUND);
    assertThat(otherCustomer).isEqualTo(unknown);
  }
}
