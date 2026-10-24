package com.shop.orders.orders.domain;

import com.shop.orders.sharedkernel.DomainError;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Commande d'un client : lignes figées à l'achat et total cohérent avec les lignes. */
public final class Order {

  public static final int MAX_LINES = 50;
  public static final int MIN_QUANTITY = 1;
  public static final int MAX_QUANTITY = 100;

  private final OrderId id;
  private final CustomerId customerId;
  private final Instant createdAt;
  private final List<OrderLine> lines;
  private final Money total;

  private Order(
      OrderId id, CustomerId customerId, Instant createdAt, List<OrderLine> lines, Money total) {
    this.id = id;
    this.customerId = customerId;
    this.createdAt = createdAt;
    this.lines = List.copyOf(lines);
    this.total = total;
  }

  /**
   * Crée une commande : 1 à 50 lignes, quantités de 1 à 100, produits distincts, une seule devise.
   */
  public static Result<Order> place(
      OrderId id, CustomerId customerId, Instant createdAt, List<OrderLine> lines) {
    DomainError invalid =
        validateItems(
            lines.stream().map(l -> new RequestedItem(l.productId(), l.quantity())).toList());
    if (invalid != null) {
      return new Result.Failure<>(invalid);
    }
    var currency = lines.getFirst().unitPrice().currency();
    if (lines.stream().anyMatch(l -> !l.unitPrice().currency().equals(currency))) {
      return new Result.Failure<>(OrderErrors.mixedCurrencies());
    }
    Money total = lines.getFirst().lineTotal();
    for (OrderLine line : lines.subList(1, lines.size())) {
      total = total.add(line.lineTotal());
    }
    return new Result.Success<>(new Order(id, customerId, createdAt, lines, total));
  }

  /** Vérifie une demande avant tout appel au catalogue. */
  public static Result<List<RequestedItem>> checkItems(List<RequestedItem> items) {
    DomainError invalid = validateItems(items);
    if (invalid != null) {
      return new Result.Failure<>(invalid);
    }
    return new Result.Success<>(items);
  }

  /** Reconstitue une commande déjà enregistrée, sans revalidation. */
  public static Order restore(
      OrderId id, CustomerId customerId, Instant createdAt, List<OrderLine> lines, Money total) {
    return new Order(id, customerId, createdAt, lines, total);
  }

  private static @Nullable DomainError validateItems(List<RequestedItem> items) {
    if (items.isEmpty() || items.size() > MAX_LINES) {
      return OrderErrors.validation("Une commande compte de 1 à " + MAX_LINES + " lignes");
    }
    Set<ProductId> seen = new HashSet<>();
    for (RequestedItem item : items) {
      if (item.quantity() < MIN_QUANTITY || item.quantity() > MAX_QUANTITY) {
        return OrderErrors.validation(
            "La quantité d'une ligne va de " + MIN_QUANTITY + " à " + MAX_QUANTITY);
      }
      if (!seen.add(item.productId())) {
        return OrderErrors.validation("Un produit ne peut figurer qu'une fois dans la commande");
      }
    }
    return null;
  }

  /** Aucun événement n'est émis par cette feature. */
  public List<Object> pullEvents() {
    return List.of();
  }

  public OrderId id() {
    return id;
  }

  public CustomerId customerId() {
    return customerId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public List<OrderLine> lines() {
    return lines;
  }

  public Money total() {
    return total;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof Order order && id.equals(order.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
