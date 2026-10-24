package com.shop.orders.orders.domain;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Port : persistance des commandes. */
public interface OrderRepository {

  void save(Order order);

  /** Une commande d'un autre client est absente, comme une commande inconnue. */
  Optional<Order> findByIdAndCustomer(OrderId id, CustomerId customerId);

  /**
   * Résumés des commandes d'un client, les plus récentes d'abord (date de création puis identifiant
   * décroissants), strictement après {@code after} s'il est donné, au plus {@code limit}.
   */
  List<OrderSummary> findPageByCustomer(
      CustomerId customerId, @Nullable OrderCursor after, int limit);
}
