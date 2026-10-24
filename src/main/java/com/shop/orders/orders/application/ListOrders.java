package com.shop.orders.orders.application;

import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.OrderCursor;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.OrderSummary;
import com.shop.orders.sharedkernel.Result;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Liste les commandes d'un client, par pages à curseur, les plus récentes d'abord. */
public final class ListOrders {

  public static final int MIN_LIMIT = 1;
  public static final int MAX_LIMIT = 100;

  private final OrderRepository repository;

  public ListOrders(OrderRepository repository) {
    this.repository = repository;
  }

  public Result<OrderPage> execute(CustomerId customerId, @Nullable OrderCursor after, int limit) {
    if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
      return new Result.Failure<>(
          OrderErrors.validation("limit doit aller de " + MIN_LIMIT + " à " + MAX_LIMIT));
    }
    List<OrderSummary> fetched = repository.findPageByCustomer(customerId, after, limit + 1);
    if (fetched.size() <= limit) {
      return new Result.Success<>(new OrderPage(fetched, null));
    }
    List<OrderSummary> items = fetched.subList(0, limit);
    return new Result.Success<>(new OrderPage(items, items.getLast().cursor()));
  }
}
