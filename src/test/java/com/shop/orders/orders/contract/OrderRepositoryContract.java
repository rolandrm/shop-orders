package com.shop.orders.orders.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderCursor;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderLine;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.OrderSummary;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Suite de contrat du port {@link OrderRepository}, exécutée contre le dépôt en mémoire (unitaire)
 * et contre l'adaptateur PostgreSQL (intégration). Chaque test part d'un dépôt vide.
 */
public abstract class OrderRepositoryContract {

  protected static final CustomerId C1 = new CustomerId(uuid(0xC1));
  protected static final CustomerId C2 = new CustomerId(uuid(0xC2));
  private static final Instant T0 = Instant.parse("2026-10-22T10:00:00Z");

  protected abstract OrderRepository repository();

  @Test
  public void savesThenReadsTwoLines() {
    Order order = order(1, C1, T0);
    repository().save(order);

    Optional<Order> read = repository().findByIdAndCustomer(order.id(), C1);

    assertThat(read).isPresent();
    assertThat(read.get()).usingRecursiveComparison().isEqualTo(order);
    assertThat(read.get().lines()).hasSize(2);
    assertThat(read.get().total().amountAsString()).isEqualTo("30.00");
    assertThat(read.get().lines().get(0).lineTotal().amountAsString()).isEqualTo("20.00");
  }

  @Test
  public void ownerSeesOrderOtherCustomerAndUnknownDoNot() {
    Order order = order(1, C1, T0);
    repository().save(order);

    assertThat(repository().findByIdAndCustomer(order.id(), C1)).isPresent();
    assertThat(repository().findByIdAndCustomer(order.id(), C2)).isEmpty();
    assertThat(repository().findByIdAndCustomer(new OrderId(uuid(999)), C1)).isEmpty();
  }

  @Test
  public void pagesOfTwoOverThreeOrdersIgnoreOtherCustomers() {
    Order oldest = order(1, C1, T0);
    Order middle = order(2, C1, T0.plusSeconds(10));
    Order newest = order(3, C1, T0.plusSeconds(20));
    Order other = order(4, C2, T0.plusSeconds(30));
    for (Order o : List.of(middle, other, oldest, newest)) {
      repository().save(o);
    }

    List<OrderSummary> first = repository().findPageByCustomer(C1, null, 2);
    assertThat(first).extracting(OrderSummary::id).containsExactly(newest.id(), middle.id());
    assertThat(first.get(0).itemCount()).isEqualTo(2);
    assertThat(first.get(0).total().amountAsString()).isEqualTo("30.00");
    assertThat(first.get(0).createdAt()).isEqualTo(newest.createdAt());

    OrderCursor cursor = first.get(1).cursor();
    List<OrderSummary> second = repository().findPageByCustomer(C1, cursor, 2);
    assertThat(second).extracting(OrderSummary::id).containsExactly(oldest.id());
  }

  @Test
  public void ordersWithSameCreationInstantAreNeitherLostNorDuplicated() {
    List<Order> orders = List.of(order(1, C1, T0), order(2, C1, T0), order(3, C1, T0));
    orders.forEach(repository()::save);

    List<OrderId> seen = new ArrayList<>();
    OrderCursor cursor = null;
    while (true) {
      List<OrderSummary> page = repository().findPageByCustomer(C1, cursor, 2);
      page.forEach(s -> seen.add(s.id()));
      if (page.size() < 2) {
        break;
      }
      cursor = page.get(page.size() - 1).cursor();
    }

    assertThat(seen).hasSize(3).doesNotHaveDuplicates();
    assertThat(seen).containsExactlyInAnyOrderElementsOf(orders.stream().map(Order::id).toList());
    assertThat(seen).isSortedAccordingTo(java.util.Comparator.<OrderId>reverseOrder());
  }

  /** Commande de deux lignes : 10.00 EUR x 2 et 2.50 EUR x 4, total 30.00 EUR. */
  protected static Order order(long n, CustomerId customer, Instant createdAt) {
    List<OrderLine> lines =
        List.of(
            new OrderLine(new ProductId(uuid(1000 + n)), "Clavier", Money.of("10.00", "EUR"), 2),
            new OrderLine(new ProductId(uuid(2000 + n)), "Souris", Money.of("2.50", "EUR"), 4));
    Result<Order> placed = Order.place(new OrderId(uuid(n)), customer, createdAt, lines);
    assertThat(placed).isInstanceOf(Result.Success.class);
    return ((Result.Success<Order>) placed).value();
  }

  private static UUID uuid(long n) {
    return new UUID(0x018f000000007000L, 0x8000000000000000L | n);
  }
}
