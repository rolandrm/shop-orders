package com.shop.orders.orders.fakes;

import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderCursor;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.OrderSummary;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Dépôt en mémoire, même ordre de liste que l'adaptateur PostgreSQL. */
public final class InMemoryOrderRepository implements OrderRepository {

  private final Map<OrderId, Order> orders = new LinkedHashMap<>();

  @Override
  public void save(Order order) {
    orders.put(order.id(), order);
  }

  @Override
  public Optional<Order> findByIdAndCustomer(OrderId id, CustomerId customerId) {
    return Optional.ofNullable(orders.get(id)).filter(o -> o.customerId().equals(customerId));
  }

  @Override
  public List<OrderSummary> findPageByCustomer(
      CustomerId customerId, @Nullable OrderCursor after, int limit) {
    return orders.values().stream()
        .filter(o -> o.customerId().equals(customerId))
        .filter(o -> after == null || after.isBefore(o.createdAt(), o.id()))
        .sorted(Comparator.comparing(Order::createdAt).thenComparing(Order::id).reversed())
        .limit(limit)
        .map(o -> new OrderSummary(o.id(), o.createdAt(), o.total(), o.lines().size()))
        .toList();
  }

  public int count() {
    return orders.size();
  }
}
