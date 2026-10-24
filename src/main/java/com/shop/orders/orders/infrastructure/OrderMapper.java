package com.shop.orders.orders.infrastructure;

import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderLine;
import com.shop.orders.orders.domain.OrderSummary;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.sharedkernel.Money;
import java.util.Currency;
import java.util.List;

/** Conversion à la main entre l'objet de domaine et l'entité JPA. */
final class OrderMapper {

  private OrderMapper() {}

  static OrderEntity toEntity(Order order) {
    List<OrderLineEmbeddable> lines =
        order.lines().stream()
            .map(
                l ->
                    new OrderLineEmbeddable(
                        l.productId().value(),
                        l.productName(),
                        l.unitPrice().amount(),
                        l.quantity(),
                        l.lineTotal().amount()))
            .toList();
    return new OrderEntity(
        order.id().value(),
        order.customerId().value(),
        order.createdAt(),
        order.total().currency().getCurrencyCode(),
        order.total().amount(),
        lines);
  }

  static Order toDomain(OrderEntity entity) {
    Currency currency = Currency.getInstance(entity.currency());
    List<OrderLine> lines =
        entity.lines().stream()
            .map(
                l ->
                    new OrderLine(
                        new ProductId(l.productId()),
                        l.productName(),
                        new Money(l.unitPriceAmount(), currency),
                        l.quantity()))
            .toList();
    return Order.restore(
        new OrderId(entity.id()),
        new CustomerId(entity.customerId()),
        entity.createdAt(),
        lines,
        new Money(entity.totalAmount(), currency));
  }

  static OrderSummary toSummary(OrderSummaryRow row) {
    return new OrderSummary(
        new OrderId(row.id()),
        row.createdAt(),
        new Money(row.totalAmount(), Currency.getInstance(row.currency())),
        row.itemCount());
  }
}
