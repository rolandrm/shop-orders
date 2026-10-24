package com.shop.orders.orders.infrastructure;

import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderCursor;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.OrderSummary;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** Adaptateur PostgreSQL du port {@link OrderRepository}. */
@Repository
class JpaOrderRepository implements OrderRepository {

  private final OrderJpaRepository jpa;

  JpaOrderRepository(OrderJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void save(Order order) {
    jpa.save(OrderMapper.toEntity(order));
  }

  @Override
  public Optional<Order> findByIdAndCustomer(OrderId id, CustomerId customerId) {
    return jpa.findByIdAndCustomerId(id.value(), customerId.value()).map(OrderMapper::toDomain);
  }

  @Override
  public List<OrderSummary> findPageByCustomer(
      CustomerId customerId, @Nullable OrderCursor after, int limit) {
    List<OrderSummaryRow> rows =
        after == null
            ? jpa.firstPage(customerId.value(), Limit.of(limit))
            : jpa.pageAfter(
                customerId.value(), after.createdAt(), after.id().value(), Limit.of(limit));
    return rows.stream().map(OrderMapper::toSummary).toList();
  }
}
