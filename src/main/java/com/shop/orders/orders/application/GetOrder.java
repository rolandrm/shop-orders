package com.shop.orders.orders.application;

import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.sharedkernel.Result;

/**
 * Lit une commande du client. Une commande d'un autre client donne le même échec qu'un identifiant
 * inconnu. Ne consulte jamais le catalogue.
 */
public final class GetOrder {

  private final OrderRepository repository;

  public GetOrder(OrderRepository repository) {
    this.repository = repository;
  }

  public Result<Order> execute(OrderId id, CustomerId customerId) {
    return repository
        .findByIdAndCustomer(id, customerId)
        .<Result<Order>>map(Result.Success::new)
        .orElseGet(() -> new Result.Failure<>(OrderErrors.orderNotFound()));
  }
}
